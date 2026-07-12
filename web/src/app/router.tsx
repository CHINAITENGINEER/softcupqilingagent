import { lazy, Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { ShellLayout } from './layout/ShellLayout'
import { Empty } from '../components/ui/Empty'
const DashboardPage = lazy(() => import('../pages/dashboard/DashboardPage'))
const ChatPage = lazy(() => import('../pages/chat/ChatPage'))
const ApprovalCenterPage = lazy(() => import('../pages/approvals/ApprovalCenterPage'))
const AuditTracePage = lazy(() => import('../pages/audit/AuditTracePage'))
const KnowledgePage = lazy(() => import('../pages/knowledge/KnowledgePage'))
const SettingsPage = lazy(() => import('../pages/settings/SettingsPage'))
const load = (Page: React.LazyExoticComponent<React.ComponentType>) => <Suspense fallback={<main className="route-loading"><Empty title="Loading console module" detail="Preparing protected workspace…"/></main>}><Page/></Suspense>
export const router = createBrowserRouter([{ element: <ShellLayout/>, children: [{ path: '/', element: load(DashboardPage) }, { path: '/chat', element: load(ChatPage) }, { path: '/approvals', element: load(ApprovalCenterPage) }, { path: '/audit', element: load(AuditTracePage) }, { path: '/knowledge', element: load(KnowledgePage) }, { path: '/settings', element: load(SettingsPage) }] }])
