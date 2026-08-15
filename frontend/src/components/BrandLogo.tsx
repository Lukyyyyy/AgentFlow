interface BrandLogoProps {
  compact?: boolean;
  className?: string;
}

const BrandMark = () => (
  <svg viewBox="0 0 48 48" role="img" aria-label="AgentFlow" focusable="false">
    <defs>
      <linearGradient id="agentflow-gradient" x1="7" y1="6" x2="42" y2="43" gradientUnits="userSpaceOnUse">
        <stop stopColor="#5CE1FF" />
        <stop offset="0.48" stopColor="#3B82F6" />
        <stop offset="1" stopColor="#7868FF" />
      </linearGradient>
    </defs>
    <path d="M9.5 36.5 22.1 10.9c.8-1.7 3.1-1.7 4 0l12.4 25.6M14.3 29.1h19.4" fill="none" stroke="url(#agentflow-gradient)" strokeWidth="4.2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M24 13.4v15.7" fill="none" stroke="#72E7FF" strokeWidth="2.2" strokeLinecap="round" opacity=".75" />
    <circle cx="24" cy="9.2" r="4.2" fill="#5CE1FF" />
    <circle cx="9.3" cy="37.2" r="4.2" fill="#3B82F6" />
    <circle cx="38.7" cy="37.2" r="4.2" fill="#7868FF" />
    <circle cx="24" cy="29.1" r="3.2" fill="#0B1220" stroke="#72E7FF" strokeWidth="2" />
  </svg>
);

const BrandLogo = ({ compact = false, className = '' }: BrandLogoProps) => (
  <div className={`agentflow-logo ${compact ? 'is-compact' : ''} ${className}`.trim()}>
    <span className="agentflow-logo-mark"><BrandMark /></span>
    {!compact && <span className="agentflow-logo-word">AgentFlow</span>}
  </div>
);

export default BrandLogo;
