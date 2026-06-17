import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import MainLayout from './components/Layout/MainLayout.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import SagaDefinitions from './pages/SagaDefinitions.jsx'
import SagaDefinitionEditor from './pages/SagaDefinitionEditor.jsx'
import SagaInstances from './pages/SagaInstances.jsx'
import SagaInstanceDetail from './pages/SagaInstanceDetail.jsx'
import DeadLetterQueue from './pages/DeadLetterQueue.jsx'

function App() {
  const { isAuthenticated } = useAuthStore()

  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <MainLayout>
      <Routes>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/definitions" element={<SagaDefinitions />} />
        <Route path="/definitions/new" element={<SagaDefinitionEditor />} />
        <Route path="/definitions/:id/edit" element={<SagaDefinitionEditor />} />
        <Route path="/instances" element={<SagaInstances />} />
        <Route path="/instances/:id" element={<SagaInstanceDetail />} />
        <Route path="/dead-letter" element={<DeadLetterQueue />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </MainLayout>
  )
}

export default App
