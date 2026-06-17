import { useState, useEffect } from 'react'
import { 
  Card, Descriptions, Table, Button, Space, Tag, Modal, 
  message, Timeline, Drawer, Collapse 
} from 'antd'
import { 
  ArrowLeftOutlined, 
  PauseCircleOutlined, 
  PlayCircleOutlined, 
  ReloadOutlined,
  RollbackOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'
import dayjs from 'dayjs'

const SagaInstanceDetail = () => {
  const navigate = useNavigate()
  const { id } = useParams()
  const { isAdmin } = useAuthStore()
  const [instance, setInstance] = useState(null)
  const [steps, setSteps] = useState([])
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(false)
  const [stepDetailVisible, setStepDetailVisible] = useState(false)
  const [selectedStep, setSelectedStep] = useState(null)

  useEffect(() => {
    loadInstance()
    loadSteps()
    
    const timer = setInterval(() => {
      loadInstance()
      loadSteps()
    }, 5000)

    return () => clearInterval(timer)
  }, [id])

  const loadInstance = async () => {
    try {
      const res = await api.get(`/saga-instances/${id}`)
      setInstance(res.data.data)
    } catch (error) {
      message.error('加载实例详情失败')
    }
  }

  const loadSteps = async () => {
    try {
      const res = await api.get(`/saga-instances/${id}/steps`)
      setSteps(res.data.data || [])
    } catch (error) {
      // Silently fail
    }
  }

  const handlePause = async () => {
    try {
      await api.post(`/saga-instances/${id}/pause`)
      message.success('实例已暂停')
      loadInstance()
    } catch (error) {
      message.error('暂停失败')
    }
  }

  const handleResume = async () => {
    try {
      await api.post(`/saga-instances/${id}/resume`)
      message.success('实例已恢复')
      loadInstance()
    } catch (error) {
      message.error('恢复失败')
    }
  }

  const handleCompensate = async () => {
    Modal.confirm({
      title: '确认手动补偿',
      content: '确定要对该实例执行手动补偿操作吗？',
      onOk: async () => {
        try {
          await api.post(`/saga-instances/${id}/compensate`)
          message.success('补偿已启动')
          loadInstance()
        } catch (error) {
          message.error('补偿启动失败')
        }
      }
    })
  }

  const handleViewStep = (step) => {
    setSelectedStep(step)
    setStepDetailVisible(true)
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

  const getStepStatusTag = (status) => {
    const statusMap = {
      PENDING: { color: 'default', text: '待执行' },
      RUNNING: { color: 'processing', text: '执行中' },
      COMPLETED: { color: 'success', text: '已完成' },
      FAILED: { color: 'error', text: '已失败' },
      SKIPPED: { color: 'default', text: '已跳过' },
      TIMED_OUT: { color: 'warning', text: '超时' }
    }
    const info = statusMap[status] || { color: 'default', text: status }
    return <Tag color={info.color}>{info.text}</Tag>
  }

  const stepColumns = [
    {
      title: '序号',
      dataIndex: 'executionOrder',
      key: 'executionOrder',
      width: 60,
      render: (v) => v + 1
    },
    {
      title: '步骤名称',
      dataIndex: 'stepName',
      key: 'stepName',
      width: 150
    },
    {
      title: '类型',
      dataIndex: 'stepType',
      key: 'stepType',
      width: 100,
      render: (type) => {
        const typeMap = {
          SEQUENTIAL: '顺序',
          PARALLEL: '并行',
          SYNC_POINT: '同步点'
        }
        return typeMap[type] || type
      }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => getStepStatusTag(status)
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 80
    },
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      key: 'startedAt',
      width: 160,
      render: t => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '结束时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 160,
      render: t => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" onClick={() => handleViewStep(record)}>
          详情
        </Button>
      )
    }
  ]

  if (!instance) {
    return <div>加载中...</div>
  }

  return (
    <div>
      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/instances')}>
              返回
            </Button>
            <span>Saga实例详情 - {instance.id}</span>
            {getStatusTag(instance.status)}
          </Space>
        }
        extra={
          isAdmin() && (
            <Space>
              {instance.status === 'RUNNING' && (
                <Button icon={<PauseCircleOutlined />} onClick={handlePause}>
                  暂停
                </Button>
              )}
              {instance.status === 'PAUSED' && (
                <Button icon={<PlayCircleOutlined />} onClick={handleResume}>
                  恢复
                </Button>
              )}
              {(instance.status === 'FAILED' || instance.status === 'NEED_INTERVENTION') && (
                <Button icon={<RollbackOutlined />} onClick={handleCompensate}>
                  手动补偿
                </Button>
              )}
            </Space>
          )
        }
      >
        <Descriptions bordered column={2} size="small">
          <Descriptions.Item label="实例ID">{instance.id}</Descriptions.Item>
          <Descriptions.Item label="Saga名称">{instance.sagaDefinitionName}</Descriptions.Item>
          <Descriptions.Item label="版本">v{instance.sagaDefinitionVersion}</Descriptions.Item>
          <Descriptions.Item label="业务标识">{instance.correlationId}</Descriptions.Item>
          <Descriptions.Item label="状态">{getStatusTag(instance.status)}</Descriptions.Item>
          <Descriptions.Item label="开始时间">
            {instance.startedAt ? dayjs(instance.startedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="结束时间">
            {instance.completedAt ? dayjs(instance.completedAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">
            {dayjs(instance.createdAt).format('YYYY-MM-DD HH:mm:ss')}
          </Descriptions.Item>
          {instance.errorMessage && (
            <Descriptions.Item label="错误信息" span={2}>
              <span style={{ color: 'red' }}>{instance.errorMessage}</span>
            </Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      <Card title="步骤执行记录" style={{ marginTop: 16 }}>
        <Table
          dataSource={steps}
          columns={stepColumns}
          rowKey="id"
          size="small"
          pagination={false}
        />
      </Card>

      <Drawer
        title="步骤详情"
        placement="right"
        width={500}
        open={stepDetailVisible}
        onClose={() => setStepDetailVisible(false)}
      >
        {selectedStep && (
          <div>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="步骤ID">{selectedStep.stepId}</Descriptions.Item>
              <Descriptions.Item label="步骤名称">{selectedStep.stepName}</Descriptions.Item>
              <Descriptions.Item label="步骤类型">{selectedStep.stepType}</Descriptions.Item>
              <Descriptions.Item label="状态">{getStepStatusTag(selectedStep.status)}</Descriptions.Item>
              <Descriptions.Item label="重试次数">{selectedStep.retryCount} / {selectedStep.maxRetries}</Descriptions.Item>
              <Descriptions.Item label="超时时间">{selectedStep.timeoutSeconds}秒</Descriptions.Item>
            </Descriptions>

            <Collapse style={{ marginTop: 16 }}>
              <Collapse.Panel header="请求信息" key="1">
                <p><strong>URL:</strong> {selectedStep.requestUrl || '-'}</p>
                <p><strong>Method:</strong> {selectedStep.requestMethod || '-'}</p>
                <p><strong>Body:</strong></p>
                <pre style={{ background: '#f5f5f5', padding: 8, borderRadius: 4, overflow: 'auto' }}>
                  {selectedStep.requestBody || '无'}
                </pre>
              </Collapse.Panel>
              <Collapse.Panel header="响应信息" key="2">
                <p><strong>状态码:</strong> {selectedStep.responseStatus || '-'}</p>
                <p><strong>响应体:</strong></p>
                <pre style={{ background: '#f5f5f5', padding: 8, borderRadius: 4, overflow: 'auto', maxHeight: 200 }}>
                  {selectedStep.responseBody || '无'}
                </pre>
              </Collapse.Panel>
              {selectedStep.errorMessage && (
                <Collapse.Panel header="错误信息" key="3">
                  <p style={{ color: 'red' }}>{selectedStep.errorMessage}</p>
                </Collapse.Panel>
              )}
            </Collapse>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default SagaInstanceDetail
