package com.saga.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpressionEvaluatorService {

    private final ObjectMapper objectMapper;
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    public boolean evaluateCondition(String expression, Map<String, Object> stepsContext, Map<String, Object> inputData) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        Map<String, Object> context = buildContext(stepsContext, inputData);

        try {
            ScriptEngine engine = scriptEngineManager.getEngineByName("graal.js");
            if (engine == null) {
                engine = scriptEngineManager.getEngineByName("JavaScript");
            }
            if (engine == null) {
                throw new RuntimeException("JavaScript engine not available");
            }

            Bindings bindings = new SimpleBindings();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                bindings.put(entry.getKey(), toJsCompatibleValue(entry.getValue()));
            }

            Object result = engine.eval(expression, bindings);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return Boolean.parseBoolean(String.valueOf(result));
        } catch (Exception e) {
            log.error("Failed to evaluate condition expression: {}", expression, e);
            throw new RuntimeException("Condition evaluation failed: " + e.getMessage(), e);
        }
    }

    public String resolveTemplate(String template, Map<String, Object> stepsContext, Map<String, Object> inputData) {
        if (template == null) {
            return null;
        }

        Map<String, Object> context = buildContext(stepsContext, inputData);

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String resolvedValue = resolveExpression(expression, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(resolvedValue != null ? resolvedValue : ""));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public List<Map<String, Object>> extractTemplateReferences(String template) {
        List<Map<String, Object>> references = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return references;
        }

        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        Set<String> seenExpressions = new HashSet<>();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            if (!seenExpressions.add(expression)) {
                continue;
            }

            Map<String, Object> ref = new HashMap<>();
            ref.put("expression", expression);

            if (expression.startsWith("steps.")) {
                String withoutPrefix = expression.substring(6);
                int dotIdx = withoutPrefix.indexOf('.');
                if (dotIdx > 0) {
                    ref.put("source", "步骤响应");
                    ref.put("sourceStep", withoutPrefix.substring(0, dotIdx));
                    ref.put("fieldPath", withoutPrefix.substring(dotIdx + 1));
                } else {
                    ref.put("source", "步骤响应");
                    ref.put("sourceStep", withoutPrefix);
                    ref.put("fieldPath", "");
                }
            } else if (expression.startsWith("input.")) {
                ref.put("source", "Saga输入参数");
                ref.put("sourceStep", "-");
                ref.put("fieldPath", expression.substring(6));
            } else {
                ref.put("source", "上下文变量");
                ref.put("sourceStep", "-");
                ref.put("fieldPath", expression);
            }

            references.add(ref);
        }

        return references;
    }

    public Map<String, Object> buildContext(Map<String, Object> stepsContext, Map<String, Object> inputData) {
        Map<String, Object> context = new HashMap<>();

        Map<String, Object> steps = new HashMap<>();
        if (stepsContext != null) {
            for (Map.Entry<String, Object> entry : stepsContext.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.endsWith("_response")) {
                    String stepId = key.substring(0, key.length() - 9);
                    Map<String, Object> stepData = new HashMap<>();
                    stepData.put("response", value);
                    steps.put(stepId, stepData);
                } else {
                    steps.put(key, value);
                }
            }
        }
        context.put("steps", steps);

        if (inputData != null) {
            context.put("input", inputData);
        } else {
            context.put("input", new HashMap<>());
        }

        return context;
    }

    private String resolveExpression(String expression, Map<String, Object> context) {
        try {
            Object value;
            if (expression.startsWith("steps.") || expression.startsWith("input.")) {
                String jsonPathExpr = "$." + expression.replace('.', '/').replace("/", ".");
                try {
                    value = JsonPath.read(context, "$." + expression);
                } catch (Exception e) {
                    value = getByDotNotation(context, expression);
                }
            } else {
                value = getByDotNotation(context, expression);
            }

            if (value == null) {
                return "";
            }
            if (value instanceof String) {
                return (String) value;
            }
            if (value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to resolve expression: {}", expression, e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private Object getByDotNotation(Map<String, Object> context, String expression) {
        String[] parts = expression.split("\\.");
        Object current = context;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object toJsCompatibleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                String json = objectMapper.writeValueAsString(value);
                return objectMapper.readValue(json, Object.class);
            } catch (Exception e) {
                return value.toString();
            }
        }
        return value;
    }

    public long getRemainingSeconds(LocalDateTime startedAt, int globalTimeoutSeconds) {
        if (startedAt == null) {
            return globalTimeoutSeconds;
        }
        long elapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        return Math.max(0, globalTimeoutSeconds - elapsed);
    }

    public boolean isTimeoutExceeded(LocalDateTime startedAt, int globalTimeoutSeconds) {
        if (startedAt == null) {
            return false;
        }
        long elapsed = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        return elapsed > globalTimeoutSeconds;
    }

    public long getElapsedSeconds(LocalDateTime startedAt) {
        if (startedAt == null) {
            return 0;
        }
        return Duration.between(startedAt, LocalDateTime.now()).getSeconds();
    }
}
