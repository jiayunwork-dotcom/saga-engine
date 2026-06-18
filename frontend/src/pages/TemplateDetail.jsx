import { useState, useEffect, useMemo } from 'react'
import {
  Card, Button, Tag, Rate, Descriptions, Table, Modal, Input, Form,
  Space, message, Divider, List, Avatar, Empty, Spin, Row, Col, Statistic, Alert
} from 'antd'
import {
  ArrowLeftOutlined, ImportOutlined, DownloadOutlined,
  UserOutlined, ClockCircleOutlined, StarOutlined, StarFilled, HistoryOutlined,
  RollbackOutlined, EditOutlined
} from '@ant-design/icons'
import { useNavigate, useParams } from 'react-router-dom'
import { templateApi } from '../services/api'
import FlowEditor from '../components/FlowEditor/FlowEditor.jsx'
import dayjs from 'dayjs'

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

const STATUS_MAP = {
  PUBLISHED: { color: 'green', text: '已上架' },
  PENDING_REVIEW: { color: 'orange', text: '待审核' },
  REJECTED: { color: 'red', text: '已拒绝' },
  REVISION_REQUIRED: { color: 'warning', text: '退回修改' }
}

const TemplateDetail = () => {
  const navigate = useNavigate()
  const { id } = useParams()
  const [template, setTemplate] = useState(null)
  const [ratings, setRatings] = useState([])
  const [versions, setVersions] = useState([])
  const [loading, setLoading] = useState(false)
  const [importModalVisible, setImportModalVisible] = useState(false)
  const [ratingModalVisible, setRatingModalVisible] = useState(false)
  const [resubmitModalVisible, setResubmitModalVisible] = useState(false)
  const [importForm] = Form.useForm()
  const [ratingForm] = Form.useForm()
  const [resubmitForm] = Form.useForm()
  const [importing, setImporting] = useState(false)
  const [resubmitting, setResubmitting] = useState(false)
  const [unmetDependencies, setUnmetDependencies] = useState([])

  const currentUser = JSON.parse(localStorage.getItem('user') || 'null')

  useEffect(() => {
    loadTemplateDetail()
    loadRatings()
  }, [id])

  const loadTemplateDetail = async () => {
    setLoading(true)
    try {
      const res = await templateApi.getDetail(id)
      const data = res.data.data
      setTemplate(data)
      if (data.name) {
        loadVersions(data.name)
      }
    } catch (error) {
      message.error('加载模板详情失败')
    } finally {
      setLoading(false)
    }
  }

  const loadRatings = async () => {
    try {
      const res = await templateApi.getRatings(id)
      setRatings(res.data.data || [])
    } catch (error) {
      // ignore
    }
  }

  const loadVersions = async (name) => {
    try {
      const res = await templateApi.getVersions(name)
      setVersions(res.data.data || [])
    } catch (error) {
      // ignore
    }
  }

  const placeholderUrls = useMemo(() => {
    if (!template || !template.stepDefinition) return []
    const urls = new Map()
    template.stepDefinition.forEach((step) => {
      const forwardAction = step.forwardAction
      if (forwardAction && forwardAction.url && forwardAction.url.startsWith('template://')) {
        urls.set(forwardAction.url, {
          url: forwardAction.url,
          stepName: step.name || '未命名步骤',
          stepDesc: step.description || '',
          type: 'forwardAction'
        })
      }
      const compAction = step.compensationAction
      if (compAction && compAction.url && compAction.url.startsWith('template://')) {
        urls.set(compAction.url, {
          url: compAction.url,
          stepName: step.name || '未命名步骤',
          stepDesc: step.description || '',
          type: 'compensationAction'
        })
      }
    })
    return Array.from(urls.values())
  }, [template])

  const handleImportClick = () => {
    importForm.resetFields()
    setUnmetDependencies([])
    setImportModalVisible(true)
  }

  const handleImportConfirm = async () => {
    try {
      const values = await importForm.validateFields()
      const urlMappings = {}
      let allFilled = true

      placeholderUrls.forEach((p) => {
        const filledUrl = values[p.url]
        if (!filledUrl || !filledUrl.trim()) {
          allFilled = false
        } else {
          urlMappings[p.url] = filledUrl.trim()
        }
      })

      if (!allFilled) {
        message.error('请填写所有占位符URL')
        return
      }

      setImporting(true)
      const res = await templateApi.importTemplate({
        templateId: parseInt(id),
        urlMappings
      })
      const result = res.data.data
      if (result.unmetDependencies && result.unmetDependencies.length > 0) {
        setUnmetDependencies(result.unmetDependencies)
        message.warning('导入成功，但存在未满足的前置模板依赖')
      } else {
        message.success('模板导入成功')
      }
      setImportModalVisible(false)
      navigate(`/definitions/${result.definition.id}/edit`)
    } catch (error) {
      if (error.errorFields) {
        message.error('请填写所有必填项')
      } else {
        message.error(error.response?.data?.message || '导入失败')
      }
    } finally {
      setImporting(false)
    }
  }

  const handleRatingSubmit = async () => {
    try {
      const values = await ratingForm.validateFields()
      await templateApi.submitRating({
        templateId: parseInt(id),
        score: values.score,
        comment: values.comment || ''
      })
      message.success('评分提交成功')
      setRatingModalVisible(false)
      loadRatings()
      loadTemplateDetail()
    } catch (error) {
      if (!error.errorFields) {
        message.error('评分提交失败')
      }
    }
  }

  const handleFavoriteClick = async () => {
    try {
      await templateApi.toggleFavorite(template.id)
      setTemplate(prev => ({ ...prev, favorited: !prev.favorited }))
      message.success(template.favorited ? '已取消收藏' : '收藏成功')
    } catch (error) {
      message.error('操作失败')
    }
  }

  const handleResubmitClick = () => {
    resubmitForm.setFieldsValue({
      description: template.description,
      sceneDescription: template.sceneDescription,
      categoryTags: template.categoryTags,
      stepDefinition: template.stepDefinition,
      dependencies: template.dependencies
    })
    setResubmitModalVisible(true)
  }

  const handleResubmitConfirm = async () => {
    try {
      const values = await resubmitForm.validateFields()
      setResubmitting(true)
      await templateApi.resubmit(id, {
        name: template.name,
        version: template.version,
        description: values.description,
        sceneDescription: values.sceneDescription,
        categoryTags: values.categoryTags || [],
        stepDefinition: template.stepDefinition,
        dependencies: values.dependencies || []
      })
      message.success('重新提交成功')
      setResubmitModalVisible(false)
      loadTemplateDetail()
    } catch (error) {
      if (error.errorFields) {
        message.error('请填写所有必填项')
      } else {
        message.error(error.response?.data?.message || '提交失败')
      }
    } finally {
      setResubmitting(false)
    }
  }

  const handleDependencyClick = (depId) => {
    navigate(`/templates/${depId}`)
  }

  if (loading) {
    return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>
  }

  if (!template) {
    return <Empty description="模板不存在" />
  }

  const statusInfo = STATUS_MAP[template.status] || { color: 'default', text: template.status }
  const isPublisher = currentUser && template.publisher === currentUser.username
  const canResubmit = template.status === 'REVISION_REQUIRED' && isPublisher

  const versionColumns = [
    { title: '版本', dataIndex: 'version', key: 'version', width: 100, render: v => <Tag color="blue">v{v}</Tag> },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100, render: s => {
      const info = STATUS_MAP[s] || { color: 'default', text: s }
      return <Tag color={info.color}>{info.text}</Tag>
    }},
    { title: '发布者', dataIndex: 'publisher', key: 'publisher', width: 100 },
    { title: '发布时间', dataIndex: 'createdAt', key: 'createdAt', width: 160, render: t => dayjs(t).format('YYYY-MM-DD HH:mm') }
  ]

  return (
    <div style={{ display: 'flex', gap: 16, minHeight: 'calc(100vh - 140px)' }}>
      <div style={{ flex: 2, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Card
          size="small"
          title={
            <Space>
              <Button icon={<ArrowLeftOutlined />} size="small" onClick={() => navigate('/templates')}>
                返回
              </Button>
              <span style={{ fontSize: 16, fontWeight: 'bold' }}>模板详情 - {template.name}</span>
              <Tag color={statusInfo.color}>{statusInfo.text}</Tag>
            </Space>
          }
        >
          <div style={{ padding: '8px 0' }}>
            <h2 style={{ marginBottom: 8 }}>
              {template.name} <Tag color="blue">v{template.version}</Tag>
              <Button
                type="text"
                icon={template.favorited ? <StarFilled style={{ color: '#faad14' }} /> : <StarOutlined />}
                onClick={handleFavoriteClick}
                style={{ marginLeft: 8 }}
              >
                {template.favorited ? '已收藏' : '收藏'}
              </Button>
            </h2>

            {template.status === 'REVISION_REQUIRED' && template.reviewComment && (
              <Alert
                type="warning"
                showIcon
                message="审核退回意见"
                description={template.reviewComment}
                style={{ marginBottom: 16 }}
              />
            )}

            {template.sceneDescription && (
              <Alert
                type="info"
                showIcon
                message="适用场景"
                description={template.sceneDescription}
                style={{ marginBottom: 16 }}
              />
            )}
            <p style={{ color: '#666', lineHeight: 1.8 }}>{template.description || '暂无详细描述'}</p>

            {template.dependencyTemplates && template.dependencyTemplates.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <Divider style={{ margin: '12px 0' }} />
                <div style={{ fontWeight: 500, marginBottom: 8 }}>前置模板（依赖）：</div>
                <Space wrap>
                  {template.dependencyTemplates.map((dep, idx) => (
                    <Tag
                      key={idx}
                      color="blue"
                      style={{ cursor: 'pointer', padding: '4px 12px' }}
                      onClick={() => handleDependencyClick(dep.id)}
                    >
                      {dep.name} v{dep.version}
                    </Tag>
                  ))}
                </Space>
              </div>
            )}
          </div>
        </Card>

        <Card
          size="small"
          title={<Space><HistoryOutlined />步骤流程图预览</Space>}
          style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 400 }}
          bodyStyle={{ flex: 1, padding: 0, display: 'flex', flexDirection: 'column', minHeight: 0 }}
        >
          {template.stepDefinition && template.stepDefinition.length > 0 ? (
            <div style={{ flex: 1, minHeight: 350 }}>
              <FlowEditor
                initialNodes={template.stepDefinition}
                initialEdges={[]}
                readOnly={true}
              />
            </div>
          ) : (
            <Empty description="暂无流程步骤" style={{ padding: 40 }} />
          )}
        </Card>

        <Card size="small" title={<Space><HistoryOutlined />版本历史</Space>}>
          <Table
            dataSource={versions}
            columns={versionColumns}
            rowKey="id"
            size="small"
            pagination={false}
          />
        </Card>
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 400 }}>
        <Card size="small" title="基本信息">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="版本">
              <Tag color="blue">v{template.version}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="发布者">
              <Space><UserOutlined />{template.publisher}</Space>
            </Descriptions.Item>
            <Descriptions.Item label="发布时间">
              <Space><ClockCircleOutlined />{dayjs(template.createdAt).format('YYYY-MM-DD HH:mm')}</Space>
            </Descriptions.Item>
            <Descriptions.Item label="下载次数">
              <Space><DownloadOutlined />{template.downloadCount}</Space>
            </Descriptions.Item>
            <Descriptions.Item label="分类标签">
              <Space>
                {(template.categoryTags || []).map((tag, idx) => (
                  <Tag key={idx} color={CATEGORY_COLORS[tag] || 'default'}>{tag}</Tag>
                ))}
              </Space>
            </Descriptions.Item>
            {template.reviewer && (
              <Descriptions.Item label="审核人">
                {template.reviewer}
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>

        <Card size="small" title={<Space><StarOutlined />评分</Space>}>
          <div style={{ textAlign: 'center', marginBottom: 16 }}>
            <Statistic
              value={template.averageScore || 0}
              suffix="/ 5"
              valueStyle={{ fontSize: 28, color: '#faad14' }}
            />
            <Rate disabled value={Math.round(template.averageScore || 0)} style={{ fontSize: 20, marginTop: 8 }} />
            <div style={{ color: '#999', fontSize: 12, marginTop: 4 }}>
              {template.ratingCount || 0} 人评分
            </div>
          </div>
          <Button
            type="primary"
            icon={<StarOutlined />}
            block
            onClick={() => {
              ratingForm.resetFields()
              setRatingModalVisible(true)
            }}
          >
            我要评分
          </Button>

          {ratings.length > 0 && (
            <>
              <Divider style={{ margin: '12px 0' }} />
              <List
                size="small"
                dataSource={ratings.slice(0, 5)}
                renderItem={(item) => (
                  <List.Item style={{ padding: '8px 0' }}>
                    <List.Item.Meta
                      avatar={<Avatar size="small" icon={<UserOutlined />} />}
                      title={
                        <Space>
                          <span style={{ fontSize: 13 }}>{item.username || '用户'}</span>
                          <Rate disabled value={item.score} style={{ fontSize: 10 }} />
                        </Space>
                      }
                      description={item.comment || ''}
                    />
                  </List.Item>
                )}
              />
            </>
          )}
        </Card>

        <Card size="small" title="操作">
          {template.status === 'PUBLISHED' && (
            <Button
              type="primary"
              icon={<ImportOutlined />}
              block
              size="large"
              onClick={handleImportClick}
            >
              导入到我的Saga
            </Button>
          )}
          {canResubmit && (
            <Button
              type="primary"
              icon={<RollbackOutlined />}
              block
              size="large"
              onClick={handleResubmitClick}
            >
              重新提交审核
            </Button>
          )}
          {template.status !== 'PUBLISHED' && !canResubmit && (
            <Alert
              type="info"
              showIcon
              message="模板状态"
              description={statusInfo.text + '状态的模板暂不可导入'}
            />
          )}
        </Card>
      </div>

      <Modal
        title="导入模板 - 配置服务地址"
        open={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        width={600}
        footer={
          <Space>
            <Button onClick={() => setImportModalVisible(false)}>取消</Button>
            <Button type="primary" loading={importing} onClick={handleImportConfirm}>
              确认导入
            </Button>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          message="请为模板中的每个占位符URL填写真实的服务地址"
          style={{ marginBottom: 16 }}
        />
        {template.dependencies && template.dependencies.length > 0 && (
          <Alert
            type="warning"
            showIcon
            message="前置模板依赖"
            description={
              <div>
                该模板依赖以下前置模板，请确保您已导入：
                <ul style={{ marginTop: 8, marginBottom: 0 }}>
                  {template.dependencies.map((dep, idx) => (
                    <li key={idx}>{dep}</li>
                  ))}
                </ul>
              </div>
            }
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={importForm} layout="vertical">
          {placeholderUrls.map((p) => (
            <Form.Item
              key={p.url}
              name={p.url}
              label={
                <div>
                  <div style={{ fontWeight: 500 }}>{p.stepName} - {p.type === 'forwardAction' ? '正向操作' : '补偿操作'}</div>
                  <div style={{ fontSize: 12, color: '#999' }}>
                    占位符: <code>{p.url}</code>
                    {p.stepDesc && <span style={{ marginLeft: 8 }}>说明: {p.stepDesc}</span>}
                  </div>
                </div>
              }
              rules={[{ required: true, message: '请填写真实服务地址' }]}
            >
              <Input placeholder="例如: http://order-service:8080/api/orders" />
            </Form.Item>
          ))}
        </Form>
      </Modal>

      <Modal
        title="提交评分"
        open={ratingModalVisible}
        onCancel={() => setRatingModalVisible(false)}
        footer={
          <Space>
            <Button onClick={() => setRatingModalVisible(false)}>取消</Button>
            <Button type="primary" onClick={handleRatingSubmit}>提交</Button>
          </Space>
        }
      >
        <Form form={ratingForm} layout="vertical">
          <Form.Item
            name="score"
            label="评分"
            rules={[{ required: true, message: '请选择评分' }]}
          >
            <Rate style={{ fontSize: 32 }} />
          </Form.Item>
          <Form.Item name="comment" label="评语">
            <Input.TextArea rows={3} placeholder="请输入评语（可选）" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重新提交审核"
        open={resubmitModalVisible}
        onCancel={() => setResubmitModalVisible(false)}
        width={600}
        footer={
          <Space>
            <Button onClick={() => setResubmitModalVisible(false)}>取消</Button>
            <Button type="primary" loading={resubmitting} onClick={handleResubmitConfirm}>
              提交
            </Button>
          </Space>
        }
      >
        {template.reviewComment && (
          <Alert
            type="warning"
            showIcon
            message="修改意见"
            description={template.reviewComment}
            style={{ marginBottom: 16 }}
          />
        )}
        <Form form={resubmitForm} layout="vertical">
          <Form.Item
            name="description"
            label="模板描述"
            rules={[{ required: true, message: '请输入模板描述' }]}
          >
            <Input.TextArea rows={3} placeholder="请输入模板描述" />
          </Form.Item>
          <Form.Item
            name="sceneDescription"
            label="适用场景"
          >
            <Input.TextArea rows={2} placeholder="请输入适用场景描述（可选）" />
          </Form.Item>
          <Form.Item
            name="dependencies"
            label="依赖模板（最多3个）"
          >
            <Select
              mode="tags"
              placeholder="选择或输入依赖的模板名称"
              style={{ width: '100%' }}
              maxTagCount={3}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TemplateDetail
