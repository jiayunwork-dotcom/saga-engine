import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import ReactFlow, {
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  Controls,
  Background,
  MiniMap,
  MarkerType
} from 'reactflow'
import 'reactflow/dist/style.css'
import { 
  Drawer, Form, Input, Select, InputNumber, 
  Space, Tag, message, Typography, Divider, Button, Table, Alert, Tooltip
} from 'antd'
import { 
  PlayCircleOutlined, 
  SwapOutlined, 
  SyncOutlined,
  DeleteOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  FullscreenOutlined,
  BgColorsOutlined,
  FilterOutlined,
  DatabaseOutlined,
  QuestionCircleOutlined
} from '@ant-design/icons'
import { nodeTypes } from './CustomNodes.jsx'

const { Title, Text } = Typography

let nodeId = 0

const getId = (prefix = 'step') => {
  nodeId++
  return `${prefix}_${Date.now()}_${nodeId}`
}

const extractTemplateReferences = (template) => {
  const refs = []
  if (!template || typeof template !== 'string') return refs
  
  const regex = /\$\{([^}]+)\}/g
  const seen = new Set()
  let match
  
  while ((match = regex.exec(template)) !== null) {
    const expr = match[1].trim()
    if (seen.has(expr)) continue
    seen.add(expr)
    
    const ref = { expression: expr, key: expr }
    if (expr.startsWith('steps.')) {
      const withoutPrefix = expr.substring(6)
      const dotIdx = withoutPrefix.indexOf('.')
      if (dotIdx > 0) {
        ref.source = '步骤响应'
        ref.sourceStep = withoutPrefix.substring(0, dotIdx)
        ref.fieldPath = withoutPrefix.substring(dotIdx + 1)
      } else {
        ref.source = '步骤响应'
        ref.sourceStep = withoutPrefix
        ref.fieldPath = ''
      }
    } else if (expr.startsWith('input.')) {
      ref.source = 'Saga输入参数'
      ref.sourceStep = '-'
      ref.fieldPath = expr.substring(6)
    } else {
      ref.source = '上下文变量'
      ref.sourceStep = '-'
      ref.fieldPath = expr
    }
    refs.push(ref)
  }
  return refs
}

const FlowEditorInner = ({ initialNodes = [], initialEdges = [], onChange, readOnly = false }) => {
  const reactFlowWrapper = useRef(null)
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [reactFlowInstance, setReactFlowInstance] = useState(null)
  const [selectedNode, setSelectedNode] = useState(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [stepForm] = Form.useForm()
  const [previewBody, setPreviewBody] = useState('')
  const [currentCondition, setCurrentCondition] = useState('')

  useEffect(() => {
    if (initialNodes && initialNodes.length > 0 && nodes.length === 0) {
      const convertedNodes = convertStepsToNodes(initialNodes)
      const convertedEdges = generateEdges(convertedNodes)
      setNodes(convertedNodes)
      setEdges(convertedEdges)
      setTimeout(() => {
        if (reactFlowInstance) {
          reactFlowInstance.fitView({ padding: 0.2 })
        }
      }, 100)
    }
  }, [initialNodes])

  useEffect(() => {
    if (nodes.length > 0) {
      const steps = convertNodesToSteps(nodes)
      onChange && onChange(steps, edges)
    }
  }, [nodes, edges])

  const convertStepsToNodes = (steps) => {
    const flowNodes = []
    const startX = 300
    const startY = 50
    const yOffset = 120

    flowNodes.push({
      id: 'start',
      type: 'start',
      position: { x: startX, y: startY },
      data: { label: '开始' },
      draggable: !readOnly
    })

    steps.forEach((step, index) => {
      flowNodes.push({
        id: step.id || getId(),
        type: 'step',
        position: { x: startX, y: startY + (index + 1) * yOffset },
        data: {
          name: step.name,
          description: step.description,
          type: step.type || 'SEQUENTIAL',
          maxRetries: step.maxRetries || 3,
          timeoutSeconds: step.timeoutSeconds || 30,
          condition: step.condition || '',
          forwardAction: step.forwardAction || { url: '', method: 'POST', body: '' },
          compensationAction: step.compensationAction || { url: '', method: 'POST', body: '' },
          status: step.status
        },
        draggable: !readOnly
      })
    })

    flowNodes.push({
      id: 'end',
      type: 'end',
      position: { x: startX, y: startY + (steps.length + 1) * yOffset },
      data: { label: '结束' },
      draggable: !readOnly
    })

    return flowNodes
  }

  const generateEdges = (flowNodes) => {
    const newEdges = []
    for (let i = 0; i < flowNodes.length - 1; i++) {
      newEdges.push({
        id: `edge_${i}`,
        source: flowNodes[i].id,
        target: flowNodes[i + 1].id,
        animated: false,
        style: { stroke: '#91d5ff', strokeWidth: 2 },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: '#91d5ff'
        }
      })
    }
    return newEdges
  }

  const convertNodesToSteps = (flowNodes) => {
    return flowNodes
      .filter(node => node.type === 'step')
      .map(node => ({
        id: node.id,
        name: node.data.name,
        description: node.data.description,
        type: node.data.type,
        condition: node.data.condition,
        maxRetries: node.data.maxRetries,
        timeoutSeconds: node.data.timeoutSeconds,
        forwardAction: node.data.forwardAction,
        compensationAction: node.data.compensationAction
      }))
  }

  const onConnect = useCallback(
    (params) => {
      if (readOnly) return
      setEdges((eds) => addEdge({
        ...params,
        animated: false,
        style: { stroke: '#91d5ff', strokeWidth: 2 },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: '#91d5ff'
        }
      }, eds))
    },
    [setEdges, readOnly]
  )

  const onDragOver = useCallback((event) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event) => {
      event.preventDefault()

      if (readOnly || !reactFlowWrapper.current) return

      const type = event.dataTransfer.getData('application/reactflow')
      if (!type) return

      const bounds = reactFlowWrapper.current.getBoundingClientRect()
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top
      })

      const stepTypeMap = {
        sequential: 'SEQUENTIAL',
        parallel: 'PARALLEL',
        sync: 'SYNC_POINT'
      }

      const nameMap = {
        sequential: '顺序步骤',
        parallel: '并行步骤',
        sync: '同步点'
      }

      const newNode = {
        id: getId(),
        type: 'step',
        position,
        data: {
          name: nameMap[type],
          type: stepTypeMap[type],
          maxRetries: 3,
          timeoutSeconds: 30,
          condition: '',
          forwardAction: { url: '', method: 'POST', body: '' },
          compensationAction: { url: '', method: 'POST', body: '' }
        },
        draggable: !readOnly
      }

      setNodes((nds) => nds.concat(newNode))
      message.success('已添加步骤,请点击节点编辑属性')
    },
    [reactFlowInstance, setNodes, readOnly]
  )

  const onNodeClick = useCallback((_event, node) => {
    if (node.type !== 'step') return
    
    setSelectedNode(node)
    const forwardAction = node.data.forwardAction || { url: '', method: 'POST', body: '' }
    const compensationAction = node.data.compensationAction || { url: '', method: 'POST', body: '' }
    stepForm.setFieldsValue({
      id: node.id,
      name: node.data.name,
      description: node.data.description,
      type: node.data.type,
      condition: node.data.condition || '',
      maxRetries: node.data.maxRetries,
      timeoutSeconds: node.data.timeoutSeconds,
      forwardAction: {
        url: forwardAction.url || '',
        method: forwardAction.method || 'POST',
        body: forwardAction.body || ''
      },
      compensationAction: {
        url: compensationAction.url || '',
        method: compensationAction.method || 'POST',
        body: compensationAction.body || ''
      }
    })
    setPreviewBody(forwardAction.body || '')
    setCurrentCondition(node.data.condition || '')
    setDrawerVisible(true)
  }, [stepForm])

  const handleNodeSave = () => {
    stepForm.validateFields().then(values => {
      setNodes((nds) =>
        nds.map((node) => {
          if (node.id === selectedNode.id) {
            return {
              ...node,
              data: {
                ...node.data,
                name: values.name,
                description: values.description,
                type: values.type,
                condition: values.condition,
                maxRetries: values.maxRetries,
                timeoutSeconds: values.timeoutSeconds,
                forwardAction: values.forwardAction,
                compensationAction: values.compensationAction
              }
            }
          }
          return node
        })
      )
      setDrawerVisible(false)
      message.success('步骤已更新')
    })
  }

  const handleDeleteNode = () => {
    if (!selectedNode) return
    
    setNodes((nds) => nds.filter((node) => node.id !== selectedNode.id))
    setEdges((eds) => 
      eds.filter((edge) => edge.source !== selectedNode.id && edge.target !== selectedNode.id)
    )
    setDrawerVisible(false)
    message.success('步骤已删除')
  }

  const onDragStart = (event, nodeType) => {
    event.dataTransfer.setData('application/reactflow', nodeType)
    event.dataTransfer.effectAllowed = 'move'
  }

  const dataMappingRefs = useMemo(() => {
    const vals = stepForm.getFieldsValue(true)
    const forwardBody = vals?.forwardAction?.body || ''
    const compBody = vals?.compensationAction?.body || ''
    const forwardUrl = vals?.forwardAction?.url || ''
    const compUrl = vals?.compensationAction?.url || ''
    const all = []
    all.push(...extractTemplateReferences(forwardBody))
    all.push(...extractTemplateReferences(compBody))
    all.push(...extractTemplateReferences(forwardUrl))
    all.push(...extractTemplateReferences(compUrl))
    const seen = new Set()
    return all.filter(r => {
      if (seen.has(r.key)) return false
      seen.add(r.key)
      return true
    })
  }, [previewBody, stepForm, drawerVisible])

  const dataMappingColumns = [
    {
      title: '数据来源',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (v) => <Tag color="blue">{v}</Tag>
    },
    {
      title: '来源步骤',
      dataIndex: 'sourceStep',
      key: 'sourceStep',
      width: 120,
      render: (v) => v === '-' ? <Text type="secondary">-</Text> : <Text code>{v}</Text>
    },
    {
      title: '字段路径',
      dataIndex: 'fieldPath',
      key: 'fieldPath',
      render: (v) => v ? <Text code>{v}</Text> : <Text type="secondary">完整响应</Text>
    },
    {
      title: '表达式',
      dataIndex: 'expression',
      key: 'expression',
      render: (v) => <Text code copyable>${'{' + v + '}'}</Text>
    }
  ]

  const sidebarItems = [
    { type: 'sequential', label: '顺序步骤', icon: <PlayCircleOutlined />, color: '#1890ff', desc: '前一个步骤完成后执行' },
    { type: 'parallel', label: '并行步骤', icon: <SwapOutlined />, color: '#722ed1', desc: '与其他并行步骤同时执行' },
    { type: 'sync', label: '同步点', icon: <SyncOutlined />, color: '#fa8c16', desc: '等待所有并行分支完成' }
  ]

  const fitView = () => {
    reactFlowInstance?.fitView({ padding: 0.2 })
  }

  const zoomIn = () => {
    reactFlowInstance?.zoomIn()
  }

  const zoomOut = () => {
    reactFlowInstance?.zoomOut()
  }

  return (
    <div style={{ display: 'flex', height: '100%', width: '100%' }}>
      {!readOnly && (
        <div style={{ 
          width: 200, 
          minWidth: 200,
          background: '#fafafa', 
          padding: 16, 
          borderRight: '1px solid #e8e8e8',
          overflowY: 'auto',
          flexShrink: 0
        }}>
          <Title level={5} style={{ marginTop: 0, marginBottom: 12 }}>
            <BgColorsOutlined style={{ marginRight: 6 }} />
            节点面板
          </Title>
          <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 16 }}>
            拖拽节点到右侧画布
          </Text>
          
          {sidebarItems.map((item) => (
            <div
              key={item.type}
              draggable
              onDragStart={(e) => onDragStart(e, item.type)}
              style={{
                padding: '12px 16px',
                marginBottom: '12px',
                background: 'white',
                border: `2px dashed ${item.color}`,
                borderRadius: '6px',
                cursor: 'grab',
                transition: 'all 0.2s',
                userSelect: 'none'
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = item.color + '15'
                e.currentTarget.style.borderStyle = 'solid'
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'white'
                e.currentTarget.style.borderStyle = 'dashed'
              }}
            >
              <div style={{ color: item.color, marginBottom: 4, fontSize: 14, fontWeight: 500 }}>
                {item.icon} <span style={{ marginLeft: 6 }}>{item.label}</span>
              </div>
              <div style={{ fontSize: 11, color: '#888', lineHeight: 1.4 }}>
                {item.desc}
              </div>
            </div>
          ))}

          <Divider style={{ margin: '16px 0' }} />

          <Title level={5} style={{ marginBottom: 12 }}>操作说明</Title>
          <ul style={{ paddingLeft: 16, fontSize: 12, color: '#666', lineHeight: 1.8, margin: 0 }}>
            <li>从左侧拖拽节点到画布</li>
            <li>点击节点编辑详细属性</li>
            <li>拖拽节点调整位置</li>
            <li>从节点连接点拖出连线</li>
          </ul>
        </div>
      )}

      <div ref={reactFlowWrapper} style={{ flex: 1, height: '100%', position: 'relative' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onInit={setReactFlowInstance}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onNodeClick={onNodeClick}
          nodeTypes={nodeTypes}
          fitView
          attributionPosition="bottom-right"
          style={{ height: '100%' }}
        >
          <Background color="#e5e7eb" gap={20} />
          <MiniMap 
            nodeStrokeWidth={2} 
            zoomable 
            pannable
            style={{ width: 140, height: 100 }}
          />
          <Controls 
            showInteractive={false}
            position="bottom-left"
          />
        </ReactFlow>

        <div style={{ 
          position: 'absolute', 
          top: 12, 
          right: 12, 
          zIndex: 10,
          display: 'flex',
          gap: 6
        }}>
          <Button size="small" icon={<ZoomInOutlined />} onClick={zoomIn} />
          <Button size="small" icon={<ZoomOutOutlined />} onClick={zoomOut} />
          <Button size="small" icon={<FullscreenOutlined />} onClick={fitView}>
            适应视图
          </Button>
        </div>

        {nodes.length === 0 && (
          <div style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            textAlign: 'center',
            color: '#999',
            pointerEvents: 'none'
          }}>
            <div style={{ fontSize: 48, marginBottom: 12, opacity: 0.3 }}>📋</div>
            <div style={{ fontSize: 14 }}>从左侧拖拽节点到这里开始创建流程</div>
          </div>
        )}
      </div>

      <Drawer
        title="编辑步骤属性"
        placement="right"
        width={520}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        footer={
          readOnly ? null : (
            <Space style={{ float: 'right' }}>
              <Button onClick={() => setDrawerVisible(false)}>取消</Button>
              <Button type="primary" icon={<DeleteOutlined />} danger onClick={handleDeleteNode}>
                删除
              </Button>
              <Button type="primary" onClick={handleNodeSave}>
                保存
              </Button>
            </Space>
          )
        }
      >
        <Form
          form={stepForm}
          layout="vertical"
          disabled={readOnly}
          onValuesChange={(changed, all) => {
            if (changed?.forwardAction?.body !== undefined) {
              setPreviewBody(changed.forwardAction.body)
            }
            if (changed?.condition !== undefined) {
              setCurrentCondition(changed.condition || '')
            }
            if (changed?.compensationAction?.body !== undefined) {
              setPreviewBody(all?.forwardAction?.body || '')
            }
          }}
        >
          <Form.Item name="id" hidden>
            <Input />
          </Form.Item>
          
          <Form.Item
            name="name"
            label="步骤名称"
            rules={[{ required: true, message: '请输入步骤名称' }]}
          >
            <Input placeholder="请输入步骤名称" />
          </Form.Item>

          <Form.Item name="description" label="步骤描述">
            <Input.TextArea rows={2} placeholder="请输入步骤描述" />
          </Form.Item>

          <Form.Item
            name="type"
            label="步骤类型"
            rules={[{ required: true, message: '请选择步骤类型' }]}
          >
            <Select>
              <Select.Option value="SEQUENTIAL">
                <Tag color="blue">顺序步骤</Tag>
              </Select.Option>
              <Select.Option value="PARALLEL">
                <Tag color="purple">并行步骤</Tag>
              </Select.Option>
              <Select.Option value="SYNC_POINT">
                <Tag color="orange">同步点</Tag>
              </Select.Option>
            </Select>
          </Form.Item>

          <Divider orientation="left">
            <Space>
              <FilterOutlined />
              执行条件
              <Tooltip title="JavaScript表达式,返回true时执行该步骤,返回false时跳过。可通过 steps.stepId.response.xxx 引用前序步骤响应,通过 input.xxx 引用Saga输入参数">
                <QuestionCircleOutlined style={{ color: '#999' }} />
              </Tooltip>
            </Space>
          </Divider>

          <Form.Item
            name="condition"
            label={
              <span>
                条件表达式 
                <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                  (留空表示无条件执行)
                </Text>
              </span>
            }
          >
            <Input.TextArea 
              rows={3} 
              placeholder="例如: steps.createOrder.response.status === 'SUCCESS' &amp;&amp; input.amount > 100" 
            />
          </Form.Item>

          {currentCondition && currentCondition.trim() && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="条件表达式示例"
              description={
                <div style={{ fontSize: 12, lineHeight: 1.8 }}>
                  <div><code>steps.step1.response.success</code> - 引用步骤step1的响应success字段</div>
                  <div><code>input.amount {'<'} 1000</code> - 引用Saga输入参数amount并判断</div>
                  <div><code>steps.step1.response.code === 200 {'&&'} input.userId</code> - 多条件组合</div>
                </div>
              }
            />
          )}

          <Divider orientation="left">正向操作</Divider>
          
          <Form.Item
            name={['forwardAction', 'url']}
            label="请求URL"
            rules={[{ required: true, message: '请输入正向操作URL' }]}
          >
            <Input placeholder="https://api.example.com/do-something, 支持 ${expression} 占位符" />
          </Form.Item>

          <Form.Item
            name={['forwardAction', 'method']}
            label="请求方法"
          >
            <Select>
              <Select.Option value="POST">POST</Select.Option>
              <Select.Option value="PUT">PUT</Select.Option>
              <Select.Option value="PATCH">PATCH</Select.Option>
              <Select.Option value="GET">GET</Select.Option>
              <Select.Option value="DELETE">DELETE</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name={['forwardAction', 'body']}
            label={
              <span>
                请求体模板 
                <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                  (支持 $&#123;expression&#125; 占位符)
                </Text>
              </span>
            }
          >
            <Input.TextArea 
              rows={5} 
              placeholder={'{"orderId":"${steps.createOrder.response.id}","userId":"${input.userId}"}'}
            />
          </Form.Item>

          <Divider orientation="left">补偿操作</Divider>
          
          <Form.Item
            name={['compensationAction', 'url']}
            label="补偿URL"
          >
            <Input placeholder="https://api.example.com/compensate, 支持 ${expression} 占位符" />
          </Form.Item>

          <Form.Item
            name={['compensationAction', 'method']}
            label="补偿方法"
          >
            <Select>
              <Select.Option value="POST">POST</Select.Option>
              <Select.Option value="PUT">PUT</Select.Option>
              <Select.Option value="DELETE">DELETE</Select.Option>
              <Select.Option value="PATCH">PATCH</Select.Option>
            </Select>
          </Form.Item>

          <Form.Item
            name={['compensationAction', 'body']}
            label="补偿请求体模板"
          >
            <Input.TextArea 
              rows={3} 
              placeholder={'{"orderId":"${steps.createOrder.response.id}"}'}
            />
          </Form.Item>

          <Divider orientation="left">
            <Space>
              <DatabaseOutlined />
              数据映射预览
            </Space>
          </Divider>

          {dataMappingRefs.length > 0 ? (
            <Table
              size="small"
              dataSource={dataMappingRefs}
              columns={dataMappingColumns}
              pagination={false}
              rowKey="key"
              scroll={{ y: 240 }}
            />
          ) : (
            <Alert
              type="info"
              showIcon
              message="暂无数据映射"
              description="在URL或请求体模板中使用 $&#123;expression&#125; 占位符引用上游数据后,将在此处显示映射关系。"
            />
          )}

          <Divider orientation="left">高级配置</Divider>

          <Space.Compact style={{ width: '100%' }}>
            <Form.Item
              name="maxRetries"
              label="最大重试次数"
              style={{ flex: 1, marginRight: 8 }}
            >
              <InputNumber min={0} max={10} style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item
              name="timeoutSeconds"
              label="超时时间(秒)"
              style={{ flex: 1 }}
            >
              <InputNumber min={5} max={3600} style={{ width: '100%' }} />
            </Form.Item>
          </Space.Compact>
        </Form>
      </Drawer>
    </div>
  )
}

const FlowEditor = (props) => {
  return (
    <ReactFlowProvider>
      <FlowEditorInner {...props} />
    </ReactFlowProvider>
  )
}

export default FlowEditor
