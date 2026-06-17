import { useState, useEffect } from 'react'
import { 
  Card, Form, Input, Button, Space, Tabs, Empty, message, Tag,
  Row, Col, Statistic, Alert
} from 'antd'
import { 
  ArrowLeftOutlined, 
  SaveOutlined, 
  PlayCircleOutlined,
  SwapOutlined,
  SyncOutlined,
  InfoCircleOutlined
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
        definition: steps
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

  return (
    <div style={{ height: 'calc(100vh - 180px)' }}>
      <Card 
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/definitions')}>
              返回
            </Button>
            <span>{isEdit ? '编辑Saga定义' : '新建Saga定义'}</span>
          </Space>
        }
        extra={
          isAdmin() && (
            <Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={loading}>
              保存
            </Button>
          )
        }
        style={{ marginBottom: 16 }}
      >
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="name"
                label="Saga名称"
                rules={[{ required: true, message: '请输入Saga名称' }]}
              >
                <Input placeholder="请输入Saga名称" disabled={isEdit} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="description" label="描述">
                <Input.TextArea rows={1} placeholder="请输入描述" />
              </Form.Item>
            </Col>
          </Row>
        </Form>

        {steps.length > 0 && (
          <Row gutter={16} style={{ marginTop: 8 }}>
            <Col span={6}>
              <Statistic 
                title="总步骤数" 
                value={steps.length} 
                prefix={<PlayCircleOutlined />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="顺序步骤" 
                value={getStepTypeCount('SEQUENTIAL')} 
                prefix={<PlayCircleOutlined />}
                valueStyle={{ color: '#1890ff' }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="并行步骤" 
                value={getStepTypeCount('PARALLEL')} 
                prefix={<SwapOutlined />}
                valueStyle={{ color: '#722ed1' }}
              />
            </Col>
            <Col span={6}>
              <Statistic 
                title="同步点" 
                value={getStepTypeCount('SYNC_POINT')} 
                prefix={<SyncOutlined />}
                valueStyle={{ color: '#fa8c16' }}
              />
            </Col>
          </Row>
        )}
      </Card>

      {!isAdmin() && (
        <Alert
          message="只读模式"
          description="您当前以操作员身份登录，无法编辑Saga定义。如需编辑，请使用管理员账号登录。"
          type="warning"
          showIcon
          icon={<InfoCircleOutlined />}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card 
        title={
          <Space>
            <span>流程设计器</span>
            {!isAdmin() && <Tag color="orange">只读</Tag>}
          </Space>
        }
        style={{ height: 'calc(100% - 200px)' }}
        bodyStyle={{ padding: 0, height: '100%' }}
      >
        <Tabs
          defaultActiveKey="flow"
          items={[
            {
              key: 'flow',
              label: '可视化流程图',
              children: steps.length === 0 && !isEdit ? (
                <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Empty 
                    description={
                      <div>
                        <p>暂无流程步骤</p>
                        <p style={{ color: '#999', fontSize: 12 }}>
                          从左侧节点面板拖拽节点到画布开始创建流程
                        </p>
                      </div>
                    }
                  />
                </div>
              ) : (
                <div style={{ height: '100%', width: '100%' }}>
                  <FlowEditor 
                    initialNodes={steps}
                    initialEdges={edges}
                    onChange={handleFlowChange}
                    readOnly={!isAdmin()}
                  />
                </div>
              )
            },
            {
              key: 'json',
              label: 'JSON数据',
              children: (
                <div style={{ padding: 16 }}>
                  <pre style={{ 
                    background: '#f5f5f5', 
                    padding: 16, 
                    borderRadius: 4,
                    overflow: 'auto',
                    maxHeight: '500px'
                  }}>
                    {JSON.stringify(steps, null, 2)}
                  </pre>
                </div>
              )
            }
          ]}
        />
      </Card>
    </div>
  )
}

export default SagaDefinitionEditor
