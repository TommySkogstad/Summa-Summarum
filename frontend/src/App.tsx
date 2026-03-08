import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './lib/queryClient'
import { AuthProvider } from './context/AuthContext'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ToastProvider } from './components/Toast'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Layout } from './components/Layout'
import { Login } from './pages/Login'
import { Dashboard } from './pages/Dashboard'
import { Transactions } from './pages/Transactions'
import { TransactionForm } from './pages/TransactionForm'
import { Categories } from './pages/Categories'
import { Reports } from './pages/Reports'
import { Organizations } from './pages/Organizations'
import { Attachments } from './pages/Attachments'

function App() {
  return (
    <ErrorBoundary>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ToastProvider />
          <Routes>
            <Route path="/login" element={<Login />} />

            <Route path="/" element={
              <ProtectedRoute>
                <Layout><Dashboard /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/transaksjoner" element={
              <ProtectedRoute>
                <Layout><Transactions /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/transaksjoner/ny" element={
              <ProtectedRoute>
                <Layout><TransactionForm /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/transaksjoner/:id" element={
              <ProtectedRoute>
                <Layout><TransactionForm /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/kategorier" element={
              <ProtectedRoute>
                <Layout><Categories /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/rapporter" element={
              <ProtectedRoute>
                <Layout><Reports /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/bilag" element={
              <ProtectedRoute>
                <Layout><Attachments /></Layout>
              </ProtectedRoute>
            } />

            <Route path="/organisasjoner" element={
              <ProtectedRoute>
                <Layout><Organizations /></Layout>
              </ProtectedRoute>
            } />

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
    </ErrorBoundary>
  )
}

export default App
