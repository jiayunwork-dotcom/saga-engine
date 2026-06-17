import { Handle, Position } from 'reactflow'
import { Tag } from 'antd'
import { 
  PlayCircleOutlined, 
  SwapOutlined, 
  SyncOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined
} from '@ant-design/icons'

const getStepTypeColor = (type) => {
  const colors = {
    SEQUENTIAL: { bg: '#e6f7ff', border: '#1890ff', text: '#1890ff' },
    PARALLEL: { bg: '#f9f0ff', border: '#722ed1', text: '#722ed1' },
    SYNC_POINT: { bg: '#fff7e6', border: '#fa8c16', text: '#fa8c16' }
  }
  return colors[type] || colors.SEQUENTIAL
}

const getStatusIcon = (status) => {
  if (!status) return null
  const icons = {
    COMPLETED: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
    FAILED: <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    RUNNING: <ClockCircleOutlined style={{ color: '#1890ff' }} spin />
  }
  return icons[status]
}

const getStepTypeIcon = (type) => {
  const icons = {
    SEQUENTIAL: <PlayCircleOutlined />,
    PARALLEL: <SwapOutlined />,
    SYNC_POINT: <SyncOutlined />
  }
  return icons[type] || <PlayCircleOutlined />
}

const getStepTypeLabel = (type) => {
  const labels = {
    SEQUENTIAL: '顺序',
    PARALLEL: '并行',
    SYNC_POINT: '同步'
  }
  return labels[type] || '顺序'
}

export const StepNode = ({ data, selected }) => {
  const colors = getStepTypeColor(data.type)
  
  return (
    <div 
      style={{
        padding: '12px 16px',
        border: `2px solid ${selected ? '#1890ff' : colors.border}`,
        borderRadius: '8px',
        background: selected ? '#e6f7ff' : colors.bg,
        minWidth: '160px',
        textAlign: 'center',
        boxShadow: selected ? '0 2px 8px rgba(24, 144, 255, 0.3)' : '0 2px 4px rgba(0,0,0,0.1)',
        transition: 'all 0.2s'
      }}
    >
      <Handle 
        type="target" 
        position={Position.Top} 
        style={{ background: colors.border, width: 12, height: 12 }}
      />
      
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, marginBottom: 4 }}>
        <span style={{ color: colors.text }}>{getStepTypeIcon(data.type)}</span>
        <Tag color={colors.text} style={{ margin: 0 }}>
          {getStepTypeLabel(data.type)}
        </Tag>
        {data.status && getStatusIcon(data.status)}
      </div>
      
      <div style={{ 
        fontWeight: 'bold', 
        fontSize: '14px',
        color: '#333',
        wordBreak: 'break-word'
      }}>
        {data.name}
      </div>
      
      {data.description && (
        <div style={{ 
          fontSize: '12px', 
          color: '#888', 
          marginTop: 4,
          wordBreak: 'break-word'
        }}>
          {data.description.length > 20 ? data.description.substring(0, 20) + '...' : data.description}
        </div>
      )}
      
      <div style={{ 
        fontSize: '11px', 
        color: '#999', 
        marginTop: 6,
        display: 'flex',
        justifyContent: 'space-between'
      }}>
        <span>重试: {data.maxRetries || 3}次</span>
        <span>超时: {data.timeoutSeconds || 30}s</span>
      </div>

      <Handle 
        type="source" 
        position={Position.Bottom} 
        style={{ background: colors.border, width: 12, height: 12 }}
      />
    </div>
  )
}

export const StartNode = ({ data }) => {
  return (
    <div style={{
      padding: '10px 20px',
      border: '2px solid #52c41a',
      borderRadius: '50%',
      background: '#f6ffed',
      width: '60px',
      height: '60px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontWeight: 'bold',
      color: '#52c41a',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <Handle 
        type="source" 
        position={Position.Bottom} 
        style={{ background: '#52c41a', width: 12, height: 12 }}
      />
      开始
    </div>
  )
}

export const EndNode = ({ data }) => {
  return (
    <div style={{
      padding: '10px 20px',
      border: '2px solid #ff4d4f',
      borderRadius: '50%',
      background: '#fff2f0',
      width: '60px',
      height: '60px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontWeight: 'bold',
      color: '#ff4d4f',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
    }}>
      <Handle 
        type="target" 
        position={Position.Top} 
        style={{ background: '#ff4d4f', width: 12, height: 12 }}
      />
      结束
    </div>
  )
}

export const nodeTypes = {
  step: StepNode,
  start: StartNode,
  end: EndNode
}
