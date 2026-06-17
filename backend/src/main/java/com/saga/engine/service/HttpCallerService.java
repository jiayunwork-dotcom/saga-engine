package com.saga.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpCallerService {

    private final RestTemplate restTemplate;

    public HttpResult executeCall(String url, String method, String body, 
                                  Map<String, String> headers, int timeoutSeconds) {
        HttpResult result = new HttpResult();
        
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            if (headers != null) {
                headers.forEach(httpHeaders::set);
            }

            HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.valueOf(method.toUpperCase()),
                    entity,
                    String.class
            );

            result.setSuccess(true);
            result.setStatusCode(response.getStatusCode().value());
            result.setResponseBody(response.getBody());
            
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            result.setSuccess(false);
            result.setStatusCode(e.getStatusCode().value());
            result.setResponseBody(e.getResponseBodyAsString());
            result.setErrorMessage(e.getMessage());
        } catch (ResourceAccessException e) {
            result.setSuccess(false);
            result.setErrorMessage("Connection error: " + e.getMessage());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Unexpected error: " + e.getMessage());
        }

        return result;
    }

    public static class HttpResult {
        private boolean success;
        private int statusCode;
        private String responseBody;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
