import { useState, useEffect } from 'react'
import { Row, Col, Card, Statistic, Table, Tag } from 'antd'
import { 
  CheckCircleOutlined, 
  CloseCircleOutlined, 
  PlayCircleOutlined, 
  ExclamationCircleOutlined 
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import api from '../services/api'

const Dashboard = () => {
  const [stats, setStats] = useState({})
  const [topSteps, setTopSteps] = useState([])
  const [hourlyTrend, setHourlyTrend] = useState([])

  useEffect(() => {
    loadData()
    const timer = setInterval(loadData, 30000)
    return () => clearInterval(timer)
  }, [])

  const loadData = async () => {
    try {
      const [statsRes, stepsRes, trendRes] = await Promise.all([
        api.get('/statistics/dashboard'),
        api.get('/statistics/top-failed-steps?limit=10'),
        api.get('/statistics/hourly-trend?hours=24')
      ])
      setStats(statsRes.data.data || {})
      setTopSteps(stepsRes.data.data || [])
      setHourlyTrend(trendRes.data.data || [])
    } catch (error) {
      console.error('Failed to load dashboard data', error)
    }
  }

  const trendOption = {
    title: { text: '24小时实例数趋势', left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: hourlyTrend.map(item => {
        const d = new Date(item.hour)
        return `${d.getHours()}:00`
      })
    },
    yAxis: { type: 'value' },
    series: [{
      data: hourlyTrend.map(item => item.count),
      type: 'line',
      smooth: true,
      areaStyle: {}
    }]
  }

  const stepColumns = [
    { title: '步骤名称', dataIndex: 'stepName', key: 'stepName' },
    { title: '失败次数', dataIndex: 'failureCount', key: 'failureCount', width: 120 }
  ]

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic 
              title="总实例数" 
              value={stats.totalInstances || 0}
              prefix={<PlayCircleOutlined style={{ color: '#1890ff' }} />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic 
              title="运行中" 
              value={stats.runningCount || 0}
              valueStyle={{ color: '#1890ff' }}
              prefix={<PlayCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic 
              title="已完成" 
              value={stats.completedCount || 0}
              valueStyle={{ color: '#52c41a' }}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic 
              title="成功率" 
              value={stats.successRate || 0}
              suffix="%"
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 20 }}>
        <Col span={14}>
          <Card title="24小时趋势">
            <ReactECharts option={trendOption} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={10}>
          <Card title="失败热点步骤 Top10">
            <Table 
              dataSource={topSteps}
              columns={stepColumns}
              size="small"
              pagination={false}
              rowKey="stepName"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 20 }}>
        <Col span={6}>
          <Card>
            <Statistic 
              title="失败数" 
              value={stats.failedCount || 0}
              valueStyle={{ color: '#ff4d4f' }}
              prefix={<CloseCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic 
              title="需人工介入" 
              value={stats.needInterventionCount || 0}
              valueStyle={{ color: '#f5222d' }}
              prefix={<ExclamationCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default Dashboard
