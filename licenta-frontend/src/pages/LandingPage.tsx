import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import logo from '../assets/quickshift-logo.svg'
import teamWorkImage from '../assets/TeamWorkImage.jpg'
import schedulingImage from '../assets/Scheduling.jpg'
import './LandingPage.css'

const coverageData = [
  { day: 'Mon', morning: 85, noon: 92, evening: 74 },
  { day: 'Tue', morning: 88, noon: 94, evening: 79 },
  { day: 'Wed', morning: 83, noon: 90, evening: 71 },
  { day: 'Thu', morning: 87, noon: 96, evening: 76 },
  { day: 'Fri', morning: 92, noon: 98, evening: 82 },
  { day: 'Sat', morning: 68, noon: 74, evening: 64 },
  { day: 'Sun', morning: 62, noon: 69, evening: 58 },
]

const baselineReadiness = [51, 56, 54, 57, 59, 46, 44]
const quickShiftReadiness = [84, 87, 85, 88, 91, 76, 73]

function getCoverageClass(value: number): string {
  if (value >= 90) {
    return 'excellent'
  }
  if (value >= 75) {
    return 'good'
  }
  return 'warning'
}

function calculateAverage(values: number[]): number {
  const total = values.reduce((sum, value) => sum + value, 0)
  return Math.round(total / values.length)
}

type ThemeMode = 'light' | 'dark'

const THEME_STORAGE_KEY = 'quickshift-theme'

function resolveInitialTheme(): ThemeMode {
  const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (storedTheme === 'dark' || storedTheme === 'light') {
    return storedTheme
  }

  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return prefersDark ? 'dark' : 'light'
}

function LandingPage() {
  const [theme, setTheme] = useState<ThemeMode>(resolveInitialTheme)
  const baselineAverage = calculateAverage(baselineReadiness)
  const quickShiftAverage = calculateAverage(quickShiftReadiness)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  }, [theme])

  return (
    <main className="landing-page">
      <header className="landing-header">
        <div className="brand-wrap">
          <img src={logo} alt="QuickShift logo" className="brand-logo" />
          <div>
            <p className="eyebrow">Enterprise workforce orchestration</p>
            <p className="brand-title">QuickShift</p>
          </div>
        </div>
        <nav className="header-actions" aria-label="Primary">
          <button
            type="button"
            className="theme-toggle"
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            aria-label="Toggle color theme"
          >
            {theme === 'light' ? 'Dark mode' : 'Light mode'}
          </button>
          <Link className="cta-secondary" to="/login">
            Log in
          </Link>
          <Link className="cta-primary" to="/register">
            Start free
          </Link>
        </nav>
      </header>

      <section className="hero-section">
        <div className="hero-copy">
          <h1>Build stronger teams with smarter shift planning.</h1>
          <p className="hero-text">
            Workforce management is not just about filling slots in a calendar. It is
            about reducing burnout, protecting productivity, and creating predictable
            operations that scale with your business.
          </p>

          <ul className="hero-highlights">
            <li>Improve staffing coverage and reduce scheduling conflicts</li>
            <li>Balance part-time and full-time capacity with confidence</li>
            <li>Give managers faster, data-backed scheduling decisions</li>
          </ul>

          <p className="hero-forecast-focus">
            Financial forecasting turns scheduling from a monthly guess into a strategic
            decision. By combining prior-year performance, seasonal patterns, and expected
            daily demand, QuickShift helps you place the right people at the right time,
            reducing labor waste during quiet intervals while protecting service quality
            during peak hours.
          </p>
        </div>

        <div className="hero-visual">
          <div className="visual-card heatmap">
            <p className="card-title">Coverage map</p>
            <p className="map-subtitle">Team readiness by day and shift intensity</p>
            <p className="map-context">
              This view reflects how prepared teams are without a workforce management
              tool, and what readiness levels can be reached on average when planning
              is optimized with QuickShift.
            </p>
            <div className="comparison-strip">
              <p>
                Without dedicated tooling average readiness: <strong>{baselineAverage}%</strong>
              </p>
              <p>
                With QuickShift-style optimization average readiness: <strong>{quickShiftAverage}%</strong>
              </p>
            </div>
            <div className="heatmap-grid" role="img" aria-label="Coverage percentage by day">
              <span className="heatmap-label">Day</span>
              <span className="heatmap-label">Morning</span>
              <span className="heatmap-label">Peak</span>
              <span className="heatmap-label">Evening</span>
              {coverageData.map((item) => (
                <Fragment key={item.day}>
                  <span className="day-cell">
                    {item.day}
                  </span>
                  <span className={`coverage-cell ${getCoverageClass(item.morning)}`}>
                    {item.morning}%
                  </span>
                  <span className={`coverage-cell ${getCoverageClass(item.noon)}`}>
                    {item.noon}%
                  </span>
                  <span className={`coverage-cell ${getCoverageClass(item.evening)}`}>
                    {item.evening}%
                  </span>
                </Fragment>
              ))}
            </div>
            <div className="map-legend">
              <span><i className="dot excellent" />Excellent</span>
              <span><i className="dot good" />Good</span>
              <span><i className="dot warning" />Needs attention</span>
            </div>
          </div>

          <div className="visual-card metrics">
            <p className="card-title">Operations impact</p>
            <div className="metric-row">
              <span>Overtime hours</span>
              <strong>-18%</strong>
            </div>
            <div className="metric-row">
              <span>Missed coverage</span>
              <strong>-27%</strong>
            </div>
            <div className="metric-row">
              <span>Manager planning time</span>
              <strong>-42%</strong>
            </div>
          </div>
        </div>
      </section>

      <section className="story-section">
        <div className="section-heading">
          <p className="eyebrow">Why this matters</p>
          <h2>Scheduling quality affects your entire business</h2>
        </div>

        <div className="story-grid">
          <article>
            <h3>People stay longer</h3>
            <p>
              Fair and transparent schedules increase trust. Teams that know their
              workload in advance are less stressed and more engaged.
            </p>
          </article>
          <article>
            <h3>Customers get consistency</h3>
            <p>
              Better coverage planning means fewer service bottlenecks, shorter wait
              times, and a better customer experience.
            </p>
          </article>
          <article>
            <h3>Leaders focus on growth</h3>
            <p>
              When roster creation is faster and clearer, managers spend less time on
              spreadsheets and more time coaching teams.
            </p>
          </article>
        </div>
      </section>

      <section className="flow-section">
        <div className="section-heading">
          <p className="eyebrow">How QuickShift helps</p>
          <h2>From complexity to strategic staffing decisions</h2>
        </div>

        <div className="flow-cards">
          <article>
            <span>01</span>
            <h3>Analyze historical business data first</h3>
            <p>
              Use previous years of revenue, sales tickets, and day-by-day demand
              patterns to build financial forecasts that estimate realistic staffing
              needs, not guesswork.
            </p>
          </article>
          <article>
            <span>02</span>
            <h3>Generate next month schedule with confidence</h3>
            <p>
              Translate forecasted demand into balanced rosters for full-time and
              part-time teams, then validate expected labor cost versus service goals.
            </p>
          </article>
          <article>
            <span>03</span>
            <h3>Adapt thresholds inside the app</h3>
            <p>
              Thresholds live in-app. If the store evolves, users can quickly adjust
              staffing numbers and constraints so next month generation reflects the new
              reality.
            </p>
          </article>
        </div>

        <div className="insight-media-grid" aria-label="Scheduling and teamwork visuals">
          <img
            src={schedulingImage}
            alt="Workforce planning chart and scheduling analysis"
          />
          <img
            src={teamWorkImage}
            alt="Teamwork and planning in a professional setting"
          />
        </div>
      </section>

      <section className="closing-section">
        <h2>Ready to modernize your workforce planning?</h2>
        <div className="closing-actions">
          <Link className="cta-primary" to="/login">
            Log in
          </Link>
          <Link className="cta-secondary" to="/register">
            Register
          </Link>
        </div>
      </section>
    </main>
  )
}

export default LandingPage