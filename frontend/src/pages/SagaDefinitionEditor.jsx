import { useState, useEffect } from 'react'
import { 
  Card, Form, Input, Button, Space, Tabs, Empty, message, Tag,
  Row, Col, Statistic, Alert, Divider, InputNumber, Tooltip
} from 'antd'
import { 
  ArrowLeftOutlined, 
  SaveOutlined, 
  PlayCircleOutlined,
  SwapOutlined,
  SyncOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  ClockCircleOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'
import FlowEditor from '../components/FlowEditor/FlowEditor.jsx'

const SagaDefinitionEditor = () => {
  const navigate = useNavigate()
  const { id } = useParams()
  const { isAdmin } = useAuthStore()
  const [form] = Form.useForm()
  const isEdit = !!id
  const [steps, setSteps] = useState([])
  const [edges, setEdges] = useState([])
  const [loading, setLoading] = useState(false)
  const [globalTimeoutSeconds, setGlobalTimeoutSeconds] = useState(300)

  useEffect(() => {
    if (isEdit) {
      loadDefinition()
    }
  }, [id])

  const loadDefinition = async () => {
    setLoading(true)
    try {
      const res = await api.get(`/saga-definitions/${id}`)
      const data = res.data.data
      form.setFieldsValue({
        name: data.name,
        description: data.description
      })
      setGlobalTimeoutSeconds(data.globalTimeoutSeconds || 300)
      setSteps(data.definition || [])
    } catch (error) {
      message.error('加载Saga定义失败')
    } finally {
      setLoading(false)
    }
  }

  const handleFlowChange = (newSteps, newEdges) => {
    setSteps(newSteps)
    setEdges(newEdges)
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      
      if (steps.length === 0) {
        message.error('请至少添加一个步骤')
        return
      }

      setLoading(true)
      const requestData = {
        name: values.name,
        description: values.description,
        definition: steps,
        globalTimeoutSeconds: globalTimeoutSeconds
      }

      if (isEdit) {
        await api.put(`/saga-definitions/${id}`, requestData)
        message.success('Saga定义已更新')
      } else {
        await api.post('/saga-definitions', requestData)
        message.success('Saga定义已创建')
      }
      navigate('/definitions')
    } catch (error) {
      // Error handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  const getStepTypeCount = (type) => {
    return steps.filter(s => s.type === type).length
  }

  const shouldShowEmpty = steps.length === 0 && !isEdit && isAdmin()

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 140px)' }}>
      <Card 
        size="small"
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} size="small" onClick={() => navigate('/definitions')}>
              返回
            </Button>
            <span style={{ fontSize: 16, fontWeight: 'bold' }}>
              {isEdit ? '编辑Saga定义' : '新建Saga定义'}
            </span>
          </Space>
        }
        extra={
          isAdmin() && (
            <Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={loading} size="small">
              保存
            </Button>
          )
        }
        style={{ marginBottom: 12, flexShrink: 0 }}
      >
        <Form form={form} layout="inline">
          <Form.Item
            name="name"
            label="Saga名称"
            rules={[{ required: true, message: '请输入Saga名称' }]}
            style={{ marginBottom: 0, minWidth: 250 }}
          >
            <Input placeholder="请输入Saga名称" disabled={isEdit} size="small" />
          </Form.Item>
          <Form.Item name="description" label="描述" style={{ marginBottom: 0, flex: 1, minWidth: 300 }}>
            <Input placeholder="请输入描述" size="small" />
          </Form.Item>
          <Form.Item
            label={
              <Tooltip title="整个Saga实例从开始到结束的最大执行时间,超过后将强制中止并触发补偿">
                <span><ClockCircleOutlined style={{ marginRight: 4 }} />全局超时(秒)</span>
              </Tooltip>
            }
            style={{ marginBottom: 0 }}
          >
            <InputNumber
              min={10}
              max={86400}
              value={globalTimeoutSeconds}
              onChange={setGlobalTimeoutSeconds}
              disabled={!isAdmin()}
              size="small"
              style={{ width: 120 }}
            />
          </Form.Item>
        </Form>

        {steps.length > 0 && (
          <Row gutter={16} style={{ marginTop: 12 }}>
            <Col span={6}>
              <Statistic 
                title="总步骤数" 
                value={steps.length} 
                valueStyle={{ fontSize: 18 }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="顺序步骤" 
                value={getStepTypeCount('SEQUENTIAL')} 
                valueStyle={{ color: '#1890ff', fontSize: 18 }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="并行步骤" 
                value={getStepTypeCount('PARALLEL')} 
                valueStyle={{ color: '#722ed1', fontSize: 18 }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="同步点" 
                value={getStepTypeCount('SYNC_POINT')} 
                valueStyle={{ color: '#fa8c16', fontSize: 18 }}
              />
            </Col>
          </Row>
        )}
      </Card>

      {!isAdmin() && (
        <Alert
          message="只读模式"
          description="您当前以操作员身份登录，无法编辑Saga定义。"
          type="warning"
          showIcon
          icon={<InfoCircleOutlined />}
          style={{ marginBottom: 12, flexShrink: 0 }}
          size="small"
        />
      )}

      <Card 
        size="small"
        title={
          <Space>
            <span>流程设计器</span>
            {!isAdmin() && <Tag color="orange" style={{ marginLeft: 8 }}>只读</Tag>}
          </Space>
        }
        style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}
        bodyStyle={{ flex: 1, padding: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }}
      >
        {shouldShowEmpty ? (
          <div style={{ 
            flex: 1, 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            flexDirection: 'column'
          }}>
            <Empty 
              description={
                <div>
                  <p style={{ fontSize: 14 }}>暂无流程步骤</p>
                  <p style={{ color: '#999', fontSize: 12 }}>
                    点击下方"开始设计"按钮进入流程图编辑
                  </p>
                </div>
              }
            />
            <Button 
              type="primary" 
              icon={<PlusOutlined />} 
              style={{ marginTop: 16 }}
              onClick={() => setSteps([{ id: 'step_demo', name: '示例步骤', type: 'SEQUENTIAL' }])}
            >
              开始设计
            </Button>
          </div>
        ) : (
          <div style={{ flex: 1, minHeight: 0 }}>
            <FlowEditor 
              initialNodes={steps}
              initialEdges={edges}
              onChange={handleFlowChange}
              readOnly={!isAdmin()}
            />
          </div>
        )}
      </Card>
    </div>
  )
}

export default SagaDefinitionEditor
