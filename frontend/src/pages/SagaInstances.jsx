import { useState, useEffect } from 'react'
import { Table, Button, Input, Select, Space, Tag, Modal, Form, message, Drawer, Descriptions } from 'antd'
import { SearchOutlined, EyeOutlined, PlayCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import dayjs from 'dayjs'

const SagaInstances = () => {
  const navigate = useNavigate()
  const [instances, setInstances] = useState([])
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState()
  const [sagaName, setSagaName] = useState('')
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [triggerModalVisible, setTriggerModalVisible] = useState(false)
  const [triggerForm] = Form.useForm()

  useEffect(() => {
    loadInstances()
  }, [status, sagaName, pagination.current, pagination.pageSize])

  const loadInstances = async () => {
    setLoading(true)
    try {
      const params = {
        page: pagination.current - 1,
        size: pagination.pageSize
      }
      if (status) params.status = status
      if (sagaName) params.sagaName = sagaName

      const res = await api.get('/saga-instances', { params })
      const data = res.data.data || {}
      setInstances(data.content || [])
      setPagination(prev => ({
        ...prev,
        total: data.totalElements || data.total || 0
      }))
    } catch (error) {
      message.error('加载实例列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleTrigger = async () => {
    try {
      const values = await triggerForm.validateFields()
      const res = await api.post('/saga-instances/trigger', values)
      message.success('Saga实例已触发')
      setTriggerModalVisible(false)
      triggerForm.resetFields()
      loadInstances()
    } catch (error) {
      // Error handled by interceptor
    }
  }

  const handleView = (id) => {
    navigate(`/instances/${id}`)
  }

  const getStatusTag = (status) => {
    const statusMap = {
      CREATED: { color: 'default', text: '已创建' },
      RUNNING: { color: 'processing', text: '运行中' },
      COMPENSATING: { color: 'warning', text: '补偿中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '已失败' },
      NEED_INTERVENTION: { color: 'red', text: '需人工介入' },
      PAUSED: { color: 'default', text: '已暂停' }
    }
    const info = statusMap[status] || { color: 'default', text: status }
    return <Tag color={info.color}>{info.text}</Tag>
  }

  const columns = [
    {
      title: '实例ID',
      dataIndex: 'id',
      key: 'id',
      width: 100
    },
    {
      title: 'Saga名称',
      dataIndex: 'sagaDefinitionName',
      key: 'sagaDefinitionName',
      width: 200
    },
    {
      title: '版本',
      dataIndex: 'sagaDefinitionVersion',
      key: 'sagaDefinitionVersion',
      width: 80,
      render: v => `v${v}`
    },
    {
      title: '业务标识',
      dataIndex: 'correlationId',
      key: 'correlationId',
      width: 200,
      ellipsis: true
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => getStatusTag(status)
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 180,
      render: t => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '结束时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 180,
      render: t => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record.id)}>
          详情
        </Button>
      )
    }
  ]

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Space>
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 150 }}
            value={status}
            onChange={setStatus}
          >
            <Select.Option value="CREATED">已创建</Select.Option>
            <Select.Option value="RUNNING">运行中</Select.Option>
            <Select.Option value="COMPENSATING">补偿中</Select.Option>
            <Select.Option value="COMPLETED">已完成</Select.Option>
            <Select.Option value="FAILED">已失败</Select.Option>
            <Select.Option value="NEED_INTERVENTION">需人工介入</Select.Option>
            <Select.Option value="PAUSED">已暂停</Select.Option>
          </Select>
          <Input
            placeholder="搜索Saga名称"
            prefix={<SearchOutlined />}
            style={{ width: 250 }}
            value={sagaName}
            onChange={(e) => setSagaName(e.target.value)}
            allowClear
          />
        </Space>
        <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => setTriggerModalVisible(true)}>
          触发Saga
        </Button>
      </div>

      <Table
        dataSource={instances}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{
          ...pagination,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条记录`
        }}
        onChange={(p) => setPagination(prev => ({ ...prev, current: p.current, pageSize: p.pageSize }))}
      />

      <Modal
        title="触发Saga实例"
        open={triggerModalVisible}
        onCancel={() => setTriggerModalVisible(false)}
        onOk={handleTrigger}
        destroyOnClose
      >
        <Form form={triggerForm} layout="vertical">
          <Form.Item
            name="sagaName"
            label="Saga名称"
            rules={[{ required: true, message: '请输入Saga名称' }]}
          >
            <Input placeholder="请输入Saga定义名称" />
          </Form.Item>
          <Form.Item
            name="correlationId"
            label="业务标识"
            rules={[{ required: true, message: '请输入业务标识' }]}
          >
            <Input placeholder="用于幂等控制的唯一业务标识" />
          </Form.Item>
          <Form.Item name="version" label="版本号">
            <Input placeholder="留空则使用最新版本" />
          </Form.Item>
          <Form.Item name="inputData" label="输入参数(JSON)">
            <Input.TextArea rows={4} placeholder='{"key": "value"}' />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SagaInstances
