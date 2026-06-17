import { useState, useEffect } from 'react'
import { Table, Button, Input, Space, Tag, Modal, message, Select } from 'antd'
import { PlusOutlined, SearchOutlined, EyeOutlined, EditOutlined, HistoryOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuthStore } from '../store/authStore'
import dayjs from 'dayjs'

const SagaDefinitions = () => {
  const navigate = useNavigate()
  const { isAdmin } = useAuthStore()
  const [definitions, setDefinitions] = useState([])
  const [loading, setLoading] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [versionModalVisible, setVersionModalVisible] = useState(false)
  const [selectedName, setSelectedName] = useState('')
  const [versions, setVersions] = useState([])

  useEffect(() => {
    loadDefinitions()
  }, [searchKeyword])

  const loadDefinitions = async () => {
    setLoading(true)
    try {
      const res = await api.get('/saga-definitions', {
        params: { keyword: searchKeyword }
      })
      setDefinitions(res.data.data || [])
    } catch (error) {
      message.error('加载Saga定义失败')
    } finally {
      setLoading(false)
    }
  }

  const showVersions = async (name) => {
    setSelectedName(name)
    try {
      const res = await api.get(`/saga-definitions/name/${name}/versions`)
      setVersions(res.data.data || [])
      setVersionModalVisible(true)
    } catch (error) {
      message.error('加载版本历史失败')
    }
  }

  const handleCreate = () => {
    navigate('/definitions/new')
  }

  const handleEdit = (id) => {
    navigate(`/definitions/${id}/edit`)
  }

  const handleView = (id) => {
    navigate(`/definitions/${id}/edit`)
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      width: 200
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '当前版本',
      dataIndex: 'version',
      key: 'version',
      width: 100,
      render: (v) => <Tag color="blue">v{v}</Tag>
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 120
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      render: (t) => dayjs(t).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleView(record.id)}>
            查看
          </Button>
          {isAdmin() && (
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record.id)}>
              编辑
            </Button>
          )}
          <Button type="link" size="small" icon={<HistoryOutlined />} onClick={() => showVersions(record.name)}>
            版本
          </Button>
        </Space>
      )
    }
  ]

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <Input
          placeholder="搜索Saga名称"
          prefix={<SearchOutlined />}
          style={{ width: 300 }}
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          allowClear
        />
        {isAdmin() && (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建Saga
          </Button>
        )}
      </div>

      <Table
        dataSource={definitions}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title={`${selectedName} 版本历史`}
        open={versionModalVisible}
        onCancel={() => setVersionModalVisible(false)}
        footer={null}
        width={600}
      >
        <Table
          dataSource={versions}
          rowKey="id"
          size="small"
          pagination={false}
          columns={[
            { title: '版本', dataIndex: 'version', key: 'version', width: 80, render: v => `v${v}` },
            { title: '描述', dataIndex: 'description', key: 'description' },
            { title: '创建人', dataIndex: 'createdBy', key: 'createdBy', width: 100 },
            { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: t => dayjs(t).format('YYYY-MM-DD HH:mm') }
          ]}
        />
      </Modal>
    </div>
  )
}

export default SagaDefinitions
