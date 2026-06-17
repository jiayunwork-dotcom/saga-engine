import { useState, useEffect } from 'react'
import { Table, Button, Space, Tag, Modal, message, Card, Descriptions } from 'antd'
import { ReloadOutlined, CheckCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import api from '../services/api'
import dayjs from 'dayjs'

const DeadLetterQueue = () => {
  const [dlqList, setDlqList] = useState([])
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState('PENDING')

  useEffect(() => {
    loadDeadLetterQueue()
    const timer = setInterval(loadDeadLetterQueue, 10000)
    return () => clearInterval(timer)
  }, [status])

  const loadDeadLetterQueue = async () => {
    setLoading(true)
    try {
      const res = await api.get('/dead-letter', {
        params: { status }
      })
      setDlqList(res.data.data || [])
    } catch (error) {
      message.error('加载死信队列失败')
    } finally {
      setLoading(false)
    }
  }

  const handleRetry = (record) => {
    Modal.confirm({
      title: '确认重试',
      content: `确定要重试步骤 "${record.stepName}" 的补偿操作吗？`,
      onOk: async () => {
        try {
          await api.post(`/dead-letter/${record.id}/retry`)
          message.success('重试已启动')
          loadDeadLetterQueue()
        } catch (error) {
          message.error('重试失败')
        }
      }
    })
  }

  const handleMarkHandled = (record) => {
    Modal.confirm({
      title: '标记为已处理',
      content: `确定要将步骤 "${record.stepName}" 标记为已处理吗？`,
      onOk: async () => {
        try {
          await api.post(`/dead-letter/${record.id}/handle`)
          message.success('已标记为已处理')
          loadDeadLetterQueue()
        } catch (error) {
          message.error('操作失败')
        }
      }
    })
  }

  const getStatusTag = (status) => {
    const statusMap = {
      PENDING: { color: 'warning', text: '待处理' },
      PROCESSING: { color: 'processing', text: '处理中' },
      RESOLVED: { color: 'success', text: '已解决' },
      HANDLED: { color: 'default', text: '已人工处理' }
    }
    const info = statusMap[status] || { color: 'default', text: status }
    return <Tag color={info.color}>{info.text}</Tag>
  }

  const getFailureTypeTag = (type) => {
    const typeMap = {
      STEP_EXECUTION_FAILED: { color: 'red', text: '步骤执行失败' },
      COMPENSATION_FAILED: { color: 'orange', text: '补偿失败' },
      TIMEOUT: { color: 'warning', text: '超时' }
    }
    const info = typeMap[type] || { color: 'default', text: type }
    return <Tag color={info.color}>{info.text}</Tag>
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80
    },
    {
      title: '实例ID',
      dataIndex: 'sagaInstanceId',
      key: 'sagaInstanceId',
      width: 100
    },
    {
      title: '步骤名称',
      dataIndex: 'stepName',
      key: 'stepName',
      width: 200
    },
    {
      title: '失败类型',
      dataIndex: 'failureType',
      key: 'failureType',
      width: 120,
      render: (type) => getFailureTypeTag(type)
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status) => getStatusTag(status)
    },
    {
      title: '重试次数',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 80
    },
    {
      title: '错误信息',
      dataIndex: 'errorMessage',
      key: 'errorMessage',
      ellipsis: true
    },
    {
      title: '处理人',
      dataIndex: 'handledBy',
      key: 'handledBy',
      width: 100
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: t => dayjs(t).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          {(record.status === 'PENDING' || record.status === 'FAILED') && (
            <Button 
              type="link" 
              size="small" 
              icon={<ReloadOutlined />}
              onClick={() => handleRetry(record)}
            >
              重试
            </Button>
          )}
          {record.status === 'PENDING' && (
            <Button 
              type="link" 
              size="small" 
              icon={<CheckCircleOutlined />}
              onClick={() => handleMarkHandled(record)}
            >
              标记已处理
            </Button>
          )}
        </Space>
      )
    }
  ]

  const pendingCount = dlqList.filter(item => item.status === 'PENDING').length

  return (
    <div>
      <Card 
        title="死信队列" 
        extra={
          <Space>
            <Button.Group>
              <Button 
                type={status === 'PENDING' ? 'primary' : 'default'}
                onClick={() => setStatus('PENDING')}
              >
                待处理
              </Button>
              <Button 
                type={status === 'RESOLVED' ? 'primary' : 'default'}
                onClick={() => setStatus('RESOLVED')}
              >
                已解决
              </Button>
              <Button 
                type={status === 'HANDLED' ? 'primary' : 'default'}
                onClick={() => setStatus('HANDLED')}
              >
                已处理
              </Button>
              <Button 
                type={!status ? 'primary' : 'default'}
                onClick={() => setStatus('')}
              >
                全部
              </Button>
            </Button.Group>
            <Button icon={<ReloadOutlined />} onClick={loadDeadLetterQueue}>
              刷新
            </Button>
          </Space>
        }
      >
        {status === 'PENDING' && pendingCount > 0 && (
          <div style={{ 
            background: '#fff2f0', 
            border: '1px solid #ffccc7', 
            borderRadius: 4, 
            padding: '12px',
            marginBottom: 16,
            display: 'flex',
            alignItems: 'center',
            gap: 8
          }}>
            <ExclamationCircleOutlined style={{ color: '#ff4d4f', fontSize: 20 }} />
            <span>当前有 <strong style={{ color: '#ff4d4f' }}>{pendingCount}</strong> 条死信消息需要处理</span>
          </div>
        )}

        <Table
          dataSource={dlqList}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20 }}
          scroll={{ x: 1200 }}
        />
      </Card>
    </div>
  )
}

export default DeadLetterQueue
