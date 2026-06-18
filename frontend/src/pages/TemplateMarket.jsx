import { useState, useEffect } from 'react'
import { Card, Input, Select, Row, Col, Tag, Empty, Spin, Rate, Space, message } from 'antd'
import { SearchOutlined, AppstoreOutlined, DownloadOutlined, UserOutlined, ClockCircleOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { templateApi } from '../services/api'
import dayjs from 'dayjs'

const CATEGORIES = [
  { value: '', label: '全部分类' },
  { value: '订单处理', label: '订单处理' },
  { value: '支付流程', label: '支付流程' },
  { value: '库存管理', label: '库存管理' },
  { value: '用户注册', label: '用户注册' },
  { value: '通知推送', label: '通知推送' },
  { value: '数据同步', label: '数据同步' },
  { value: '审批流程', label: '审批流程' },
  { value: '其他', label: '其他' }
]

const CATEGORY_COLORS = {
  '订单处理': 'blue',
  '支付流程': 'green',
  '库存管理': 'orange',
  '用户注册': 'purple',
  '通知推送': 'cyan',
  '数据同步': 'geekblue',
  '审批流程': 'magenta',
  '其他': 'default'
}

const TemplateMarket = () => {
  const navigate = useNavigate()
  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [category, setCategory] = useState('')
  const [sortBy, setSortBy] = useState('createdAt')
  const [pagination, setPagination] = useState({ current: 1, pageSize: 12, total: 0 })

  useEffect(() => {
    loadTemplates()
  }, [keyword, category, sortBy, pagination.current])

  const loadTemplates = async () => {
    setLoading(true)
    try {
      const res = await templateApi.search({
        keyword: keyword || undefined,
        category: category || undefined,
        page: pagination.current - 1,
        size: pagination.pageSize,
        sortBy
      })
      const pageData = res.data.data
      setTemplates(pageData.content || [])
      setPagination(prev => ({ ...prev, total: pageData.totalElements || 0 }))
    } catch (error) {
      message.error('加载模板列表失败')
    } finally {
      setLoading(false)
    }
  }

  const handleCardClick = (id) => {
    navigate(`/templates/${id}`)
  }

  const truncateDesc = (desc, maxLen = 100) => {
    if (!desc) return '暂无描述'
    return desc.length > maxLen ? desc.substring(0, maxLen) + '...' : desc
  }

  return (
    <div>
      <div style={{ marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
        <Input
          placeholder="搜索模板名称、描述"
          prefix={<SearchOutlined />}
          style={{ width: 320 }}
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPagination(prev => ({ ...prev, current: 1 }))
          }}
          allowClear
        />
        <Space>
          <Select
            value={category}
            onChange={(v) => {
              setCategory(v)
              setPagination(prev => ({ ...prev, current: 1 }))
            }}
            style={{ width: 150 }}
            options={CATEGORIES}
          />
          <Select
            value={sortBy}
            onChange={setSortBy}
            style={{ width: 150 }}
            options={[
              { value: 'createdAt', label: '按发布时间' },
              { value: 'downloadCount', label: '按下载次数' }
            ]}
          />
        </Space>
      </div>

      <Spin spinning={loading}>
        {templates.length === 0 && !loading ? (
          <Empty description="暂无模板" />
        ) : (
          <Row gutter={[16, 16]}>
            {templates.map((template) => (
              <Col key={template.id} xs={24} sm={12} md={8}>
                <Card
                  hoverable
                  onClick={() => handleCardClick(template.id)}
                  style={{ height: '100%' }}
                  bodyStyle={{ padding: 20, display: 'flex', flexDirection: 'column', height: '100%' }}
                >
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                      <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600, flex: 1, marginRight: 8 }}>
                        {template.name}
                      </h3>
                      <Tag color="blue" style={{ flexShrink: 0 }}>v{template.version}</Tag>
                    </div>

                    <p style={{ color: '#666', fontSize: 13, marginBottom: 12, lineHeight: 1.6 }}>
                      {truncateDesc(template.description)}
                    </p>

                    <div style={{ marginBottom: 12 }}>
                      {(template.categoryTags || []).map((tag, idx) => (
                        <Tag
                          key={idx}
                          color={CATEGORY_COLORS[tag] || 'default'}
                          style={{ marginBottom: 4 }}
                        >
                          {tag}
                        </Tag>
                      ))}
                    </div>
                  </div>

                  <div style={{ borderTop: '1px solid #f0f0f0', paddingTop: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center', color: '#999', fontSize: 12 }}>
                    <Space split="·">
                      <span><UserOutlined /> {template.publisher}</span>
                      <span><DownloadOutlined /> {template.downloadCount}</span>
                      <span><ClockCircleOutlined /> {dayjs(template.createdAt).format('MM-DD')}</span>
                    </Space>
                    {template.ratingCount > 0 ? (
                      <Space size={4}>
                        <Rate disabled value={Math.round(template.averageScore)} style={{ fontSize: 12 }} />
                        <span>{template.averageScore}</span>
                      </Space>
                    ) : (
                      <span>暂无评分</span>
                    )}
                  </div>
                </Card>
              </Col>
            ))}
          </Row>
        )}

        {pagination.total > pagination.pageSize && (
          <div style={{ textAlign: 'center', marginTop: 24 }}>
            <Space>
              <a
                onClick={() => setPagination(prev => ({ ...prev, current: Math.max(1, prev.current - 1) }))}
                disabled={pagination.current <= 1}
                style={pagination.current <= 1 ? { color: '#ccc', cursor: 'not-allowed' } : {}}
              >
                上一页
              </a>
              <span>{pagination.current} / {Math.max(1, Math.ceil(pagination.total / pagination.pageSize))}</span>
              <a
                onClick={() => setPagination(prev => ({ ...prev, current: prev.current + 1 }))}
                disabled={pagination.current >= Math.ceil(pagination.total / pagination.pageSize)}
                style={pagination.current >= Math.ceil(pagination.total / pagination.pageSize) ? { color: '#ccc', cursor: 'not-allowed' } : {}}
              >
                下一页
              </a>
            </Space>
          </div>
        )}
      </Spin>
    </div>
  )
}

export default TemplateMarket
