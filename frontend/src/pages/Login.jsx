import { Form, Input, Button, message } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'

const Login = () => {
  const navigate = useNavigate()
  const { login } = useAuthStore()

  const handleSubmit = async (values) => {
    try {
      await login(values.username, values.password)
      message.success('登录成功')
      navigate('/dashboard')
    } catch (error) {
      message.error(error.response?.data?.message || '登录失败')
    }
  }

  return (
    <div className="login-container">
      <div className="login-card">
        <h2 className="login-title">Saga Engine 管理平台</h2>
        <Form
          name="login"
          onFinish={handleSubmit}
          initialValues={{ username: 'admin', password: 'admin123' }}
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input 
              prefix={<UserOutlined />} 
              placeholder="用户名" 
              size="large"
            />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              size="large"
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large">
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center', color: '#999', fontSize: '12px' }}>
            默认账号: admin/admin123 (管理员) 或 operator/operator123 (操作员)
          </div>
        </Form>
      </div>
    </div>
  )
}

export default Login
