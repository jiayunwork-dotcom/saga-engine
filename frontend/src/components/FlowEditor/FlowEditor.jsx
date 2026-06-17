import { useState, useCallback, useRef, useMemo, useEffect } from 'react'
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
  Button, Drawer, Form, Input, Select, InputNumber, 
  Space, Card, Tag, message, Typography, Divider 
} from 'antd'
import { 
  PlusOutlined, 
  PlayCircleOutlined, 
  SwapOutlined, 
  SyncOutlined,
  DeleteOutlined,
  SaveOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
  FullscreenOutlined
} from '@ant-design/icons'
import { nodeTypes } from './CustomNodes.jsx'

const { Title, Text } = Typography

let nodeId = 0

const getId = (prefix = 'step') => {
  nodeId++
  return `${prefix}_${Date.now()}_${nodeId}`
}

const FlowEditor = ({ initialNodes = [], initialEdges = [], onChange, readOnly = false }) => {
  const reactFlowWrapper = useRef(null)
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [reactFlowInstance, setReactFlowInstance] = useState(null)
  const [selectedNode, setSelectedNode] = useState(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [stepForm] = Form.useForm()

  useEffect(() => {
    if (initialNodes.length > 0 && nodes.length === 0) {
      const convertedNodes = convertStepsToNodes(initialNodes)
      const convertedEdges = generateEdges(convertedNodes)
      setNodes(convertedNodes)
      setEdges(convertedEdges)
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
          forwardAction: step.forwardAction || {},
          compensationAction: step.compensationAction || {},
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

  const generateEdges = (nodes) => {
    const edges = []
    for (let i = 0; i < nodes.length - 1; i++) {
      edges.push({
        id: `edge_${i}`,
        source: nodes[i].id,
        target: nodes[i + 1].id,
        animated: true,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: '#1890ff'
        },
        style: { stroke: '#1890ff', strokeWidth: 2 }
      })
    }
    return edges
  }

  const convertNodesToSteps = (flowNodes) => {
    return flowNodes
      .filter(node => node.type === 'step')
      .sort((a, b) => a.position.y - b.position.y)
      .map((node, index) => ({
        id: node.id,
        name: node.data.name,
        description: node.data.description,
        type: node.data.type,
        maxRetries: node.data.maxRetries,
        timeoutSeconds: node.data.timeoutSeconds,
        forwardAction: node.data.forwardAction,
        compensationAction: node.data.compensationAction,
        executionOrder: index
      }))
  }

  const onConnect = useCallback(
    (params) => {
      if (readOnly) return
      setEdges((eds) =>
        addEdge(
          {
            ...params,
            animated: true,
            markerEnd: {
              type: MarkerType.ArrowClosed,
              color: '#1890ff'
            },
            style: { stroke: '#1890ff', strokeWidth: 2 }
          },
          eds
        )
      )
    },
    [setEdges, readOnly]
  )

  const onDragOver = useCallback((event) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event) => {
      if (readOnly) return
      event.preventDefault()

      const type = event.dataTransfer.getData('application/reactflow')
      if (!type) return

      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY
      })

      const stepType = type === 'sync' ? 'SYNC_POINT' : type.toUpperCase()
      
      const newNode = {
        id: getId(),
        type: 'step',
        position,
        data: {
          name: `新步骤`,
          description: '',
          type: stepType,
          maxRetries: 3,
          timeoutSeconds: 30,
          forwardAction: {},
          compensationAction: {}
        },
        draggable: !readOnly
      }

      setNodes((nds) => nds.concat(newNode))
      message.success('已添加步骤，请点击节点编辑属性')
    },
    [reactFlowInstance, setNodes, readOnly]
  )

  const onNodeClick = useCallback((_event, node) => {
    if (node.type !== 'step') return
    
    setSelectedNode(node)
    stepForm.setFieldsValue({
      id: node.id,
      name: node.data.name,
      description: node.data.description,
      type: node.data.type,
      maxRetries: node.data.maxRetries,
      timeoutSeconds: node.data.timeoutSeconds,
      forwardAction: node.data.forwardAction,
      compensationAction: node.data.compensationAction
    })
    setDrawerVisible(true)
  }, [stepForm])

  const onNodeDoubleClick = useCallback((_event, node) => {
    if (readOnly || node.type === 'start' || node.type === 'end') return
    onNodeClick(_event, node)
  }, [onNodeClick, readOnly])

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
    <ReactFlowProvider>
      <div style={{ display: 'flex', height: '100%', width: '100%' }}>
        {!readOnly && (
          <div style={{ 
            width: '180px', 
            background: '#fafafa', 
            padding: '16px', 
            borderRight: '1px solid #e8e8e8',
            overflowY: 'auto'
          }}>
            <Title level={5} style={{ marginBottom: 16 }}>
              <PlusOutlined /> 节点面板
            </Title>
            <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 12 }}>
              拖拽节点到画布
            </Text>
            
            {sidebarItems.map((item) => (
              <div
                key={item.type}
                draggable
                onDragStart={(e) => onDragStart(e, item.type)}
                style={{
                  padding: '12px',
                  marginBottom: '12px',
                  background: 'white',
                  border: `2px dashed ${item.color}`,
                  borderRadius: '6px',
                  cursor: 'grab',
                  transition: 'all 0.2s'
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
                <div style={{ color: item.color, marginBottom: 4 }}>
                  {item.icon} <strong>{item.label}</strong>
                </div>
                <div style={{ fontSize: 11, color: '#888' }}>
                  {item.desc}
                </div>
              </div>
            ))}

            <Divider style={{ margin: '16px 0' }} />

            <Title level={5} style={{ marginBottom: 12 }}>操作提示</Title>
            <ul style={{ paddingLeft: 16, fontSize: 12, color: '#666', lineHeight: 1.8 }}>
              <li>拖拽左侧节点到画布</li>
              <li>点击节点编辑属性</li>
              <li>拖拽节点调整位置</li>
              <li>从节点底部连线到其他节点顶部</li>
            </ul>
          </div>
        )}

        <div ref={reactFlowWrapper} style={{ flexGrow: 1, position: 'relative' }}>
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
            onNodeDoubleClick={onNodeDoubleClick}
            nodeTypes={nodeTypes}
            fitView
            attributionPosition="bottom-right"
          >
            <Background color="#aaa" gap={16} />
            <MiniMap 
              nodeStrokeWidth={3} 
              zoomable 
              pannable
              style={{ width: 120, height: 100 }}
            />
            <Controls 
              showInteractive={false}
              position="bottom-left"
            />
          </ReactFlow>

          <div style={{ 
            position: 'absolute', 
            top: 10, 
            right: 10, 
            zIndex: 10,
            display: 'flex',
            gap: 8
          }}>
            <Button size="small" icon={<ZoomInOutlined />} onClick={zoomIn} />
            <Button size="small" icon={<ZoomOutOutlined />} onClick={zoomOut} />
            <Button size="small" icon={<FullscreenOutlined />} onClick={fitView}>
              适应视图
            </Button>
          </div>
        </div>
      </div>

      <Drawer
        title="编辑步骤属性"
        placement="right"
        width={500}
        open={drawerVisible}
        onClose={() => setDrawerVisible(false)}
        extra={
          <Space>
            {!readOnly && (
              <Button danger icon={<DeleteOutlined />} onClick={handleDeleteNode}>
                删除步骤
              </Button>
            )}
            {!readOnly && (
              <Button type="primary" icon={<SaveOutlined />} onClick={handleNodeSave}>
                保存
              </Button>
            )}
          </Space>
        }
        destroyOnClose
      >
        {selectedNode && (
          <Form form={stepForm} layout="vertical">
            <Form.Item name="id" hidden>
              <Input />
            </Form.Item>

            <Card size="small" title="基本信息" style={{ marginBottom: 16 }}>
              <Form.Item
                name="name"
                label="步骤名称"
                rules={[{ required: true, message: '请输入步骤名称' }]}
              >
                <Input placeholder="请输入步骤名称" disabled={readOnly} />
              </Form.Item>

              <Form.Item name="description" label="步骤描述">
                <Input.TextArea rows={2} placeholder="请输入步骤描述" disabled={readOnly} />
              </Form.Item>

              <Form.Item name="type" label="步骤类型">
                <Select disabled={readOnly}>
                  <Select.Option value="SEQUENTIAL">
                    <Tag color="blue">顺序执行</Tag> 按顺序逐个执行
                  </Select.Option>
                  <Select.Option value="PARALLEL">
                    <Tag color="purple">并行执行</Tag> 与其他并行步骤同时执行
                  </Select.Option>
                  <Select.Option value="SYNC_POINT">
                    <Tag color="orange">同步点</Tag> 等待所有并行分支完成
                  </Select.Option>
                </Select>
              </Form.Item>

              <Space wrap>
                <Form.Item name="maxRetries" label="最大重试次数" style={{ marginBottom: 0 }}>
                  <InputNumber min={0} max={10} disabled={readOnly} />
                </Form.Item>

                <Form.Item name="timeoutSeconds" label="超时时间(秒)" style={{ marginBottom: 0 }}>
                  <InputNumber min={1} max={3600} disabled={readOnly} />
                </Form.Item>
              </Space>
            </Card>

            <Card size="small" title="正向操作" style={{ marginBottom: 16 }}>
              <Form.Item name={['forwardAction', 'url']} label="请求URL">
                <Input placeholder="http://service/api/action" disabled={readOnly} />
              </Form.Item>

              <Form.Item name={['forwardAction', 'method']} label="请求方法">
                <Select disabled={readOnly}>
                  <Select.Option value="GET">GET</Select.Option>
                  <Select.Option value="POST">POST</Select.Option>
                  <Select.Option value="PUT">PUT</Select.Option>
                  <Select.Option value="DELETE">DELETE</Select.Option>
                </Select>
              </Form.Item>

              <Form.Item name={['forwardAction', 'body']} label="请求体模板">
                <Input.TextArea 
                  rows={4} 
                  placeholder='{"orderId": "${orderId}", "amount": ${amount}}'
                  disabled={readOnly}
                />
              </Form.Item>
            </Card>

            <Card size="small" title="补偿操作">
              <Form.Item name={['compensationAction', 'url']} label="补偿URL">
                <Input placeholder="http://service/api/compensate" disabled={readOnly} />
              </Form.Item>

              <Form.Item name={['compensationAction', 'method']} label="请求方法">
                <Select disabled={readOnly}>
                  <Select.Option value="POST">POST</Select.Option>
                  <Select.Option value="PUT">PUT</Select.Option>
                  <Select.Option value="DELETE">DELETE</Select.Option>
                </Select>
              </Form.Item>

              <Form.Item name={['compensationAction', 'body']} label="补偿请求体模板">
                <Input.TextArea 
                  rows={4} 
                  placeholder='{"orderId": "${response_orderId}"}'
                  disabled={readOnly}
                />
              </Form.Item>

              <div style={{ fontSize: 12, color: '#888', marginTop: 8 }}>
                <strong>提示:</strong> 使用 <code>${变量名}</code> 引用Saga输入参数或上一步响应数据
              </div>
            </Card>
          </Form>
        )}
      </Drawer>
    </ReactFlowProvider>
  )
}

export default FlowEditor
