import { Link } from 'react-router-dom'
import './RequestDemoPage.css'

function RequestDemoPage() {
  return (
    <main className="demo-shell">
      <section className="demo-card">
        <Link to="/" className="demo-back-link">
          Back to home
        </Link>
        <p className="demo-eyebrow">QuickShift for future teams</p>
        <h1>Request a demo</h1>
        <p className="demo-subtitle">
          Tell us a little about your workforce planning needs. We will prepare a tailored
          walkthrough focused on staffing coverage, cost control, and team wellbeing.
        </p>

        <div className="demo-grid">
          <div className="demo-panel">
            <h2>What you will see</h2>
            <ul>
              <li>Store-level staffing forecasts and coverage insights</li>
              <li>Monthly schedule generation with full-time and part-time balance</li>
              <li>Manager-friendly approvals, notifications, and controls</li>
            </ul>
          </div>
          <div className="demo-panel demo-contact">
            <h2>Contact</h2>
            <p>Reach us directly for a faster response.</p>
            <div className="demo-contact-details">
              <span>Email</span>
              <a href="mailto:quickshift@gmail.com">quickshift@gmail.com</a>
              <span>Phone</span>
              <a href="tel:+40784280700">+40784280700</a>
            </div>
            <div className="demo-actions">
              <a className="demo-primary" href="mailto:quickshift@gmail.com">
                Email for a demo
              </a>
              <a className="demo-secondary" href="tel:+40784280700">
                Call now
              </a>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}

export default RequestDemoPage
