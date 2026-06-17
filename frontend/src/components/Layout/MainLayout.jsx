import { Layout, Menu, Dropdown, Avatar, Space } from 'antd'
import { useNavigate, useLocation } from 'react-router-dom'
import { 
  DashboardOutlined, 
  ProjectOutlined, 
  PlayCircleOutlined, 
  WarningOutlined,
  UserOutlined,
  LogoutOutlined
} from '@ant-design/icons'
import { useAuthStore } from '../../store/authStore'

const { Header, Sider, Content, Footer } = Layout

const MainLayout = ({ children }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout, isAdmin } = useAuthStore()

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '统计仪表盘'
    },
    {
      key: '/definitions',
      icon: <ProjectOutlined />,
      label: 'Saga定义'
    },
    {
      key: '/instances',
      icon: <PlayCircleOutlined />,
      label: '实例监控'
    },
    ...(isAdmin() ? [{
      key: '/dead-letter',
      icon: <WarningOutlined />,
      label: '死信队列'
    }] : [])
  ]

  const handleMenuClick = ({ key }) => {
    navigate(key)
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout
    }
  ]

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <div className="app-logo">Saga Engine 管理平台</div>
        <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer', color: 'white' }}>
            <Avatar size="small" icon={<UserOutlined />} />
            <span>{user?.username || '用户'}</span>
          </Space>
        </Dropdown>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: '#fff' }}>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={handleMenuClick}
            style={{ height: '100%', borderRight: 0 }}
          />
        </Sider>
        <Layout style={{ padding: '0 24px 24px' }}>
          <Content className="app-content">{children}</Content>
          <Footer className="app-footer">Saga Engine ©2024 Created by Saga Team</Footer>
        </Layout>
      </Layout>
    </Layout>
  )
}

export default MainLayout
