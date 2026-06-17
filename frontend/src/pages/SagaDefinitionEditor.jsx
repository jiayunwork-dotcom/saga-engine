import { useState, useEffect } from 'react'
import { 
  Card, Form, Input, Button, Space, List, Modal, Select, 
  InputNumber, message, Drawer, Tag 
} from 'antd'
import { PlusOutlined, DeleteOutlined, EditOutlined, SaveOutlined } from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'

const SagaDefinitionEditor = () => {
  const navigate = useNavigate()
  const { id } = useParams()
  const { isAdmin } = useAuthStore()
  const [form] = Form.useForm()
  const [steps, setSteps] = useState([])
  const [stepModalVisible, setStepModalVisible] = useState(false)
  const [editingStep, setEditingStep] = useState(null)
  const [stepForm] = Form.useForm()
  const isEdit = !!id

  useEffect(() => {
    if (isEdit) {
      loadDefinition()
    }
  }, [id])

  const loadDefinition = async () => {
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
    }
  }

  const handleAddStep = () => {
    setEditingStep(null)
    stepForm.resetFields()
    stepForm.setFieldsValue({
      type: 'SEQUENTIAL',
      maxRetries: 3,
      timeoutSeconds: 30,
      retryStrategy: 'EXPONENTIAL'
    })
    setStepModalVisible(true)
  }

  const handleEditStep = (index) => {
    const step = steps[index]
    setEditingStep({ ...step, index })
    stepForm.setFieldsValue({
      name: step.name,
      id: step.id,
      type: step.type || 'SEQUENTIAL',
      description: step.description,
      maxRetries: step.maxRetries || 3,
      timeoutSeconds: step.timeoutSeconds || 30,
      forwardAction: step.forwardAction || {},
      compensationAction: step.compensationAction || {}
    })
    setStepModalVisible(true)
  }

  const handleDeleteStep = (index) => {
    const newSteps = [...steps]
    newSteps.splice(index, 1)
    setSteps(newSteps)
  }

  const handleStepSave = () => {
    stepForm.validateFields().then(values => {
      const stepData = {
        id: values.id || `step_${Date.now()}`,
        name: values.name,
        type: values.type,
        description: values.description,
        maxRetries: values.maxRetries,
        timeoutSeconds: values.timeoutSeconds,
        forwardAction: values.forwardAction,
        compensationAction: values.compensationAction
      }

      if (editingStep && editingStep.index !== undefined) {
        const newSteps = [...steps]
        newSteps[editingStep.index] = stepData
        setSteps(newSteps)
      } else {
        setSteps([...steps, stepData])
      }
      setStepModalVisible(false)
      message.success('步骤已保存')
    })
  }

  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      
      if (steps.length === 0) {
        message.error('请至少添加一个步骤')
        return
      }

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
    }
  }

  const moveStep = (fromIndex, toIndex) => {
    if (toIndex < 0 || toIndex >= steps.length) return
    const newSteps = [...steps]
    const [removed] = newSteps.splice(fromIndex, 1)
    newSteps.splice(toIndex, 0, removed)
    setSteps(newSteps)
  }

  const getStepTypeTag = (type) => {
    const colors = {
      SEQUENTIAL: 'blue',
      PARALLEL: 'purple',
      SYNC_POINT: 'orange'
    }
    const labels = {
      SEQUENTIAL: '顺序',
      PARALLEL: '并行',
      SYNC_POINT: '同步点'
    }
    return <Tag color={colors[type]}>{labels[type]}</Tag>
  }

  return (
    <div>
      <Card 
        title={isEdit ? '编辑Saga定义' : '新建Saga定义'} 
        extra={
          <Space>
            <Button onClick={() => navigate('/definitions')}>取消</Button>
            {isAdmin() && (
              <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
                保存
              </Button>
            )}
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Saga名称"
            rules={[{ required: true, message: '请输入Saga名称' }]}
          >
            <Input placeholder="请输入Saga名称" disabled={isEdit} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="请输入描述" />
          </Form.Item>
        </Form>
      </Card>

      <Card 
        style={{ marginTop: 16 }}
        title="流程步骤"
        extra={
          isAdmin() && (
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAddStep}>
              添加步骤
            </Button>
          )
        }
      >
        <List
          dataSource={steps}
          renderItem={(item, index) => (
            <List.Item
              actions={isAdmin() ? [
                <Button type="link" size="small" onClick={() => moveStep(index, index - 1)} disabled={index === 0}>
                  上移
                </Button>,
                <Button type="link" size="small" onClick={() => moveStep(index, index + 1)} disabled={index === steps.length - 1}>
                  下移
                </Button>,
                <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditStep(index)}>
                  编辑
                </Button>,
                <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => handleDeleteStep(index)}>
                  删除
                </Button>
              ] : []}
            >
              <List.Item.Meta
                title={
                  <Space>
                    <span>{index + 1}. {item.name}</span>
                    {getStepTypeTag(item.type)}
                  </Space>
                }
                description={item.description || '无描述'}
              />
            </List.Item>
          )}
        />
        {steps.length === 0 && (
          <div style={{ textAlign: 'center', padding: '40px', color: '#999' }}>
            暂无步骤，点击"添加步骤"开始创建流程
          </div>
        )}
      </Card>

      <Modal
        title={editingStep ? '编辑步骤' : '添加步骤'}
        open={stepModalVisible}
        onCancel={() => setStepModalVisible(false)}
        onOk={handleStepSave}
        width={600}
        destroyOnClose
      >
        <Form form={stepForm} layout="vertical">
          <Form.Item name="id" label="步骤ID">
            <Input placeholder="步骤唯一标识" />
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
          <Form.Item name="type" label="步骤类型">
            <Select>
              <Select.Option value="SEQUENTIAL">顺序执行</Select.Option>
              <Select.Option value="PARALLEL">并行执行</Select.Option>
              <Select.Option value="SYNC_POINT">同步点</Select.Option>
            </Select>
          </Form.Item>

          <Card size="small" title="正向操作" style={{ marginBottom: 16 }}>
            <Form.Item name={['forwardAction', 'url']} label="请求URL">
              <Input placeholder="http://service/api/action" />
            </Form.Item>
            <Form.Item name={['forwardAction', 'method']} label="请求方法">
              <Select>
                <Select.Option value="GET">GET</Select.Option>
                <Select.Option value="POST">POST</Select.Option>
                <Select.Option value="PUT">PUT</Select.Option>
                <Select.Option value="DELETE">DELETE</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name={['forwardAction', 'body']} label="请求体模板">
              <Input.TextArea rows={3} placeholder='{"key": "${value}"}' />
            </Form.Item>
          </Card>

          <Card size="small" title="补偿操作" style={{ marginBottom: 16 }}>
            <Form.Item name={['compensationAction', 'url']} label="补偿URL">
              <Input placeholder="http://service/api/compensate" />
            </Form.Item>
            <Form.Item name={['compensationAction', 'method']} label="请求方法">
              <Select>
                <Select.Option value="POST">POST</Select.Option>
                <Select.Option value="PUT">PUT</Select.Option>
                <Select.Option value="DELETE">DELETE</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item name={['compensationAction', 'body']} label="补偿请求体模板">
              <Input.TextArea rows={3} placeholder='{"id": "${response_id}"}' />
            </Form.Item>
          </Card>

          <Form.Item name="maxRetries" label="最大重试次数">
            <InputNumber min={0} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="timeoutSeconds" label="超时时间(秒)">
            <InputNumber min={1} max={3600} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SagaDefinitionEditor
