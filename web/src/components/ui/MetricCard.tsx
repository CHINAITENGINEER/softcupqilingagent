import type { ReactNode } from 'react'
import { Card } from './Card'
export function MetricCard({ icon, value, label, note, tone }: { icon: ReactNode; value: string; label: string; note: string; tone: string }) { return <Card className={`metric ${tone}`}><span className="metric-icon">{icon}</span><div><h2>{value}</h2><b>{label}</b><small>{note}</small></div></Card> }
