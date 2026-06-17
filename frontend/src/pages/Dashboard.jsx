import { useState, useEffect, useMemo } from 'react'
import { Row, Col, Card, Statistic, Table, Tag, Empty, Spin } from 'antd'
import { 
  CheckCircleOutlined, 
  CloseCircleOutlined, 
  PlayCircleOutlined, 
  ExclamationCircleOutlined,
  LineChartOutlined
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import api from '../services/api'
import dayjs from 'dayjs'

const Dashboard = () => {
  const [stats, setStats] = useState({})
  const [topSteps, setTopSteps] = useState([])
  const [hourlyTrend, setHourlyTrend] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadData()
    const timer = setInterval(loadData, 30000)
    return () => clearInterval(timer)
  }, [])

  const loadData = async () => {
    setLoading(true)
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
    } finally {
      setLoading(false)
    }
  }

  const trendOption = useMemo(() => {
    const xData = hourlyTrend.map(item => {
      if (!item.hour) return ''
      const d = dayjs(item.hour)
      return d.format('HH:00')
    })

    const yData = hourlyTrend.map(item => item.count || 0)

    return {
      title: { 
        text: '24小时实例数趋势', 
        left: 'center', 
        textStyle: { fontSize: 14, fontWeight: 'normal' } 
      },
      tooltip: { 
        trigger: 'axis',
        formatter: (params) => {
          if (params && params.length > 0) {
            return `${params[0].axisValue}<br/>实例数: ${params[0].value}`
          }
          return ''
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: xData,
        axisLabel: {
          rotate: 45
        }
      },
      yAxis: {
        type: 'value',
        minInterval: 1
      },
      series: [{
        name: '实例数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
          color: '#1890ff'
        },
        itemStyle: {
          color: '#1890ff'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(24, 144, 255, 0.5)' },
              { offset: 1, color: 'rgba(24, 144, 255, 0.05)' }
            ]
          }
        },
        data: yData
      }]
    }
  }, [hourlyTrend])

  const stepColumns = [
    { title: '步骤名称', dataIndex: 'stepName', key: 'stepName' },
    { 
      title: '失败次数', 
      dataIndex: 'failureCount', 
      key: 'failureCount', 
      width: 120,
      render: (count) => (
        <Tag color="red">{count}</Tag>
      )
    }
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
          <Card 
            title={
              <span>
                <LineChartOutlined style={{ marginRight: 8 }} />
                24小时实例数趋势
              </span>
            }
          >
            <Spin spinning={loading}>
              {hourlyTrend.length > 0 ? (
                <ReactECharts 
                  option={trendOption} 
                  style={{ height: 300, width: '100%' }}
                  notMerge={true}
                  lazyUpdate={true}
                />
              ) : (
                <div style={{ height: 300, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Empty description="暂无数据" />
                </div>
              )}
            </Spin>
          </Card>
        </Col>
        <Col span={10}>
          <Card title="失败热点步骤 Top10">
            <Spin spinning={loading}>
              {topSteps.length > 0 ? (
                <Table 
                  dataSource={topSteps}
                  columns={stepColumns}
                  size="small"
                  pagination={false}
                  rowKey="stepName"
                />
              ) : (
                <div style={{ padding: '40px 0', textAlign: 'center', color: '#999' }}>
                  暂无失败数据
                </div>
              )}
            </Spin>
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
        <Col span={6}>
          <Card>
            <Statistic 
              title="补偿中" 
              value={stats.compensatingCount || 0}
              valueStyle={{ color: '#faad14' }}
              prefix={<PlayCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic 
              title="已暂停" 
              value={stats.pausedCount || 0}
              valueStyle={{ color: '#722ed1' }}
              prefix={<PlayCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default Dashboard
