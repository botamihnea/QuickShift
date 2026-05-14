import { Fragment, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import logo from '../assets/quickshift-logo.svg'
import teamWorkImage from '../assets/TeamWorkImage.jpg'
import schedulingImage from '../assets/Scheduling.jpg'
import './LandingPage.css'

const coverageData = [
  { dayKey: 'mon', morning: 85, noon: 92, evening: 74 },
  { dayKey: 'tue', morning: 88, noon: 94, evening: 79 },
  { dayKey: 'wed', morning: 83, noon: 90, evening: 71 },
  { dayKey: 'thu', morning: 87, noon: 96, evening: 76 },
  { dayKey: 'fri', morning: 92, noon: 98, evening: 82 },
  { dayKey: 'sat', morning: 68, noon: 74, evening: 64 },
  { dayKey: 'sun', morning: 62, noon: 69, evening: 58 },
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
type LanguageMode = 'en' | 'ro'

const THEME_STORAGE_KEY = 'quickshift-theme'
const LANGUAGE_STORAGE_KEY = 'quickshift-language'

function resolveInitialTheme(): ThemeMode {
  const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (storedTheme === 'dark' || storedTheme === 'light') {
    return storedTheme
  }

  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  return prefersDark ? 'dark' : 'light'
}

function resolveInitialLanguage(): LanguageMode {
  const storedLanguage = window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
  if (storedLanguage === 'en' || storedLanguage === 'ro') {
    return storedLanguage
  }

  return 'en'
}

function LandingPage() {
  const [theme, setTheme] = useState<ThemeMode>(resolveInitialTheme)
  const [language, setLanguage] = useState<LanguageMode>(resolveInitialLanguage)
  const baselineAverage = calculateAverage(baselineReadiness)
  const quickShiftAverage = calculateAverage(quickShiftReadiness)
  const isRomanian = language === 'ro'
  const uiText = useMemo(
    () =>
      isRomanian
        ? {
            eyebrow: 'Orchestrare forta de munca enterprise',
            themeToggleLight: 'Mod intunecat',
            themeToggleDark: 'Mod luminos',
            translateToRomanian: 'Tradu in romana',
            translateToEnglish: 'Tradu in engleza',
            ctaInterested: 'Interesat de QuickShift?',
            ctaRequestDemo: 'Cere un demo',
            ctaEmployee: 'Deja angajat?',
            ctaLogin: 'Autentificare',
            ctaNewEmployee: 'Angajat recent?',
            ctaRegister: 'Inregistrare',
            heroTitle: 'Construieste echipe mai puternice cu planificare inteligenta a turelor.',
            heroText:
              'Managementul fortei de munca nu inseamna doar completarea calendarului. Inseamna reducerea epuizarii, protejarea productivitatii si crearea unor operatiuni predictibile care cresc odata cu afacerea.',
            heroHighlights: [
              'Imbunatateste acoperirea personalului si reduce conflictele de program',
              'Echilibreaza part-time si full-time cu incredere',
              'Ofera managerilor decizii rapide bazate pe date',
            ],
            heroForecast:
              'Prognoza financiara transforma programarea dintr-o presupunere lunara intr-o decizie strategica. Prin combinarea performantei anilor anteriori, a sezonalitatii si a cererii zilnice, QuickShift te ajuta sa pui oamenii potriviti la momentul potrivit, reducand risipa de ore in perioadele calme si protejand calitatea serviciilor in orele de varf.',
            coverageTitle: 'Harta acoperire',
            coverageSubtitle: 'Pregatirea echipei pe zi si intensitate',
            coverageContext:
              'Aceasta vedere arata cat de pregatite sunt echipele fara un instrument dedicat si ce nivel mediu se poate atinge cand planificarea este optimizata cu QuickShift.',
            coverageWithout: 'Fara unelte dedicate, pregatire medie:',
            coverageWith: 'Cu optimizare in stil QuickShift, pregatire medie:',
            heatmapLabelDay: 'Zi',
            heatmapLabelMorning: 'Dimineata',
            heatmapLabelPeak: 'Varf',
            heatmapLabelEvening: 'Seara',
            heatmapAria: 'Acoperire procentuala pe zi',
            dayLabels: {
              mon: 'Lun',
              tue: 'Mar',
              wed: 'Mie',
              thu: 'Joi',
              fri: 'Vin',
              sat: 'Sam',
              sun: 'Dum',
            },
            legendExcellent: 'Excelent',
            legendGood: 'Bun',
            legendWarning: 'Necesita atentie',
            impactTitle: 'Impact operational',
            impactOvertime: 'Ore suplimentare',
            impactMissed: 'Acoperire ratata',
            impactPlanning: 'Timp planificare manager',
            storyEyebrow: 'De ce conteaza',
            storyTitle: 'Calitatea programarii afecteaza intreaga afacere',
            storyPeopleTitle: 'Oamenii raman mai mult',
            storyPeopleBody:
              'Programarile echitabile si transparente cresc increderea. Echipele care isi stiu din timp volumul de munca sunt mai putin stresate si mai implicate.',
            storyCustomerTitle: 'Clientii primesc consistenta',
            storyCustomerBody:
              'Planificarea mai buna inseamna mai putine blocaje, timpi de asteptare mai mici si o experienta mai buna pentru clienti.',
            storyLeaderTitle: 'Liderii se concentreaza pe crestere',
            storyLeaderBody:
              'Cand programarea este mai rapida si mai clara, managerii petrec mai putin timp in foi de calcul si mai mult timp in formarea echipelor.',
            flowEyebrow: 'Cum ajuta QuickShift',
            flowTitle: 'De la complexitate la decizii strategice de personal',
            flowCardOneTitle: 'Analizeaza mai intai datele istorice de business',
            flowCardOneBody:
              'Foloseste veniturile anilor trecuti, bonurile de vanzare si cererea pe zile pentru a construi prognoze realiste, nu presupuneri.',
            flowCardTwoTitle: 'Genereaza programul lunii urmatoare cu incredere',
            flowCardTwoBody:
              'Transforma cererea prognozata in programe echilibrate pentru echipe full-time si part-time, apoi valideaza costul muncii si obiectivele de service.',
            flowCardThreeTitle: 'Adapteaza pragurile direct in aplicatie',
            flowCardThreeBody:
              'Pragurile sunt in aplicatie. Daca magazinul evolueaza, utilizatorii pot ajusta rapid constrangerile pentru luna urmatoare.',
            schedulingAlt: 'Grafic de planificare si analiza programarii',
            teamworkAlt: 'Echipa si planificare intr-un mediu profesional',
            closingTitle: 'Esti gata sa modernizezi planificarea fortei de munca?',
            closingCta: 'Cere un demo',
          }
        : {
            eyebrow: 'Enterprise workforce orchestration',
            themeToggleLight: 'Dark mode',
            themeToggleDark: 'Light mode',
            translateToRomanian: 'Translate to Romanian',
            translateToEnglish: 'Translate to English',
            ctaInterested: 'Interested in QuickShift?',
            ctaRequestDemo: 'Request a demo',
            ctaEmployee: 'Already an employee?',
            ctaLogin: 'Log in',
            ctaNewEmployee: 'Just employed?',
            ctaRegister: 'Register',
            heroTitle: 'Build stronger teams with smarter shift planning.',
            heroText:
              'Workforce management is not just about filling slots in a calendar. It is about reducing burnout, protecting productivity, and creating predictable operations that scale with your business.',
            heroHighlights: [
              'Improve staffing coverage and reduce scheduling conflicts',
              'Balance part-time and full-time capacity with confidence',
              'Give managers faster, data-backed scheduling decisions',
            ],
            heroForecast:
              'Financial forecasting turns scheduling from a monthly guess into a strategic decision. By combining prior-year performance, seasonal patterns, and expected daily demand, QuickShift helps you place the right people at the right time, reducing labor waste during quiet intervals while protecting service quality during peak hours.',
            coverageTitle: 'Coverage map',
            coverageSubtitle: 'Team readiness by day and shift intensity',
            coverageContext:
              'This view reflects how prepared teams are without a workforce management tool, and what readiness levels can be reached on average when planning is optimized with QuickShift.',
            coverageWithout: 'Without dedicated tooling average readiness:',
            coverageWith: 'With QuickShift-style optimization average readiness:',
            heatmapLabelDay: 'Day',
            heatmapLabelMorning: 'Morning',
            heatmapLabelPeak: 'Peak',
            heatmapLabelEvening: 'Evening',
            heatmapAria: 'Coverage percentage by day',
            dayLabels: {
              mon: 'Mon',
              tue: 'Tue',
              wed: 'Wed',
              thu: 'Thu',
              fri: 'Fri',
              sat: 'Sat',
              sun: 'Sun',
            },
            legendExcellent: 'Excellent',
            legendGood: 'Good',
            legendWarning: 'Needs attention',
            impactTitle: 'Operations impact',
            impactOvertime: 'Overtime hours',
            impactMissed: 'Missed coverage',
            impactPlanning: 'Manager planning time',
            storyEyebrow: 'Why this matters',
            storyTitle: 'Scheduling quality affects your entire business',
            storyPeopleTitle: 'People stay longer',
            storyPeopleBody:
              'Fair and transparent schedules increase trust. Teams that know their workload in advance are less stressed and more engaged.',
            storyCustomerTitle: 'Customers get consistency',
            storyCustomerBody:
              'Better coverage planning means fewer service bottlenecks, shorter wait times, and a better customer experience.',
            storyLeaderTitle: 'Leaders focus on growth',
            storyLeaderBody:
              'When roster creation is faster and clearer, managers spend less time on spreadsheets and more time coaching teams.',
            flowEyebrow: 'How QuickShift helps',
            flowTitle: 'From complexity to strategic staffing decisions',
            flowCardOneTitle: 'Analyze historical business data first',
            flowCardOneBody:
              'Use previous years of revenue, sales tickets, and day-by-day demand patterns to build financial forecasts that estimate realistic staffing needs, not guesswork.',
            flowCardTwoTitle: 'Generate next month schedule with confidence',
            flowCardTwoBody:
              'Translate forecasted demand into balanced rosters for full-time and part-time teams, then validate expected labor cost versus service goals.',
            flowCardThreeTitle: 'Adapt thresholds inside the app',
            flowCardThreeBody:
              'Thresholds live in-app. If the store evolves, users can quickly adjust staffing numbers and constraints so next month generation reflects the new reality.',
            schedulingAlt: 'Workforce planning chart and scheduling analysis',
            teamworkAlt: 'Teamwork and planning in a professional setting',
            closingTitle: 'Ready to modernize your workforce planning?',
            closingCta: 'Request a demo',
          },
    [isRomanian],
  )

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  }, [theme])

  useEffect(() => {
    window.localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
  }, [language])

  return (
    <main className="landing-page">
      <header className="landing-header">
        <div className="brand-wrap">
          <img src={logo} alt="QuickShift logo" className="brand-logo" />
          <div>
            <p className="eyebrow">{uiText.eyebrow}</p>
            <p className="brand-title">QuickShift</p>
          </div>
        </div>
        <nav className="header-actions" aria-label="Primary">
          <div className="header-toggles">
            <button
              type="button"
              className="theme-toggle"
              onClick={() => setLanguage(isRomanian ? 'en' : 'ro')}
            >
              {isRomanian ? uiText.translateToEnglish : uiText.translateToRomanian}
            </button>
            <button
              type="button"
              className="theme-toggle"
              onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
              aria-label="Toggle color theme"
            >
              {theme === 'light' ? uiText.themeToggleLight : uiText.themeToggleDark}
            </button>
          </div>
          <div className="header-cta-stack">
            <div className="cta-row">
              <span className="cta-label">{uiText.ctaInterested}</span>
              <Link className="cta-primary" to="/demo">
                {uiText.ctaRequestDemo}
              </Link>
            </div>
            <div className="cta-row">
              <span className="cta-label">{uiText.ctaEmployee}</span>
              <Link className="cta-secondary" to="/login">
                {uiText.ctaLogin}
              </Link>
            </div>
            <div className="cta-row">
              <span className="cta-label">{uiText.ctaNewEmployee}</span>
              <Link className="cta-secondary" to="/register">
                {uiText.ctaRegister}
              </Link>
            </div>
          </div>
        </nav>
      </header>

      <section className="hero-section">
        <div className="hero-copy">
          <h1>{uiText.heroTitle}</h1>
          <p className="hero-text">
            {uiText.heroText}
          </p>

          <ul className="hero-highlights">
            {uiText.heroHighlights.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>

          <p className="hero-forecast-focus">
            {uiText.heroForecast}
          </p>
        </div>

        <div className="hero-visual">
          <div className="visual-card heatmap">
            <p className="card-title">{uiText.coverageTitle}</p>
            <p className="map-subtitle">{uiText.coverageSubtitle}</p>
            <p className="map-context">
              {uiText.coverageContext}
            </p>
            <div className="comparison-strip">
              <p>
                {uiText.coverageWithout} <strong>{baselineAverage}%</strong>
              </p>
              <p>
                {uiText.coverageWith} <strong>{quickShiftAverage}%</strong>
              </p>
            </div>
            <div className="heatmap-grid" role="img" aria-label={uiText.heatmapAria}>
              <span className="heatmap-label">{uiText.heatmapLabelDay}</span>
              <span className="heatmap-label">{uiText.heatmapLabelMorning}</span>
              <span className="heatmap-label">{uiText.heatmapLabelPeak}</span>
              <span className="heatmap-label">{uiText.heatmapLabelEvening}</span>
              {coverageData.map((item) => (
                <Fragment key={item.dayKey}>
                  <span className="day-cell">
                    {uiText.dayLabels[item.dayKey]}
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
              <span><i className="dot excellent" />{uiText.legendExcellent}</span>
              <span><i className="dot good" />{uiText.legendGood}</span>
              <span><i className="dot warning" />{uiText.legendWarning}</span>
            </div>
          </div>

          <div className="visual-card metrics">
            <p className="card-title">{uiText.impactTitle}</p>
            <div className="metric-row">
              <span>{uiText.impactOvertime}</span>
              <strong>-18%</strong>
            </div>
            <div className="metric-row">
              <span>{uiText.impactMissed}</span>
              <strong>-27%</strong>
            </div>
            <div className="metric-row">
              <span>{uiText.impactPlanning}</span>
              <strong>-42%</strong>
            </div>
          </div>
        </div>
      </section>

      <section className="story-section">
        <div className="section-heading">
          <p className="eyebrow">{uiText.storyEyebrow}</p>
          <h2>{uiText.storyTitle}</h2>
        </div>

        <div className="story-grid">
          <article>
            <h3>{uiText.storyPeopleTitle}</h3>
            <p>{uiText.storyPeopleBody}</p>
          </article>
          <article>
            <h3>{uiText.storyCustomerTitle}</h3>
            <p>{uiText.storyCustomerBody}</p>
          </article>
          <article>
            <h3>{uiText.storyLeaderTitle}</h3>
            <p>{uiText.storyLeaderBody}</p>
          </article>
        </div>
      </section>

      <section className="flow-section">
        <div className="section-heading">
          <p className="eyebrow">{uiText.flowEyebrow}</p>
          <h2>{uiText.flowTitle}</h2>
        </div>

        <div className="flow-cards">
          <article>
            <span>01</span>
            <h3>{uiText.flowCardOneTitle}</h3>
            <p>{uiText.flowCardOneBody}</p>
          </article>
          <article>
            <span>02</span>
            <h3>{uiText.flowCardTwoTitle}</h3>
            <p>{uiText.flowCardTwoBody}</p>
          </article>
          <article>
            <span>03</span>
            <h3>{uiText.flowCardThreeTitle}</h3>
            <p>{uiText.flowCardThreeBody}</p>
          </article>
        </div>

        <div className="insight-media-grid" aria-label="Scheduling and teamwork visuals">
          <img
            src={schedulingImage}
            alt={uiText.schedulingAlt}
          />
          <img
            src={teamWorkImage}
            alt={uiText.teamworkAlt}
          />
        </div>
      </section>

      <section className="closing-section">
        <h2>{uiText.closingTitle}</h2>
        <div className="closing-actions">
          <Link className="cta-primary closing-demo" to="/demo">
            {uiText.closingCta}
          </Link>
        </div>
      </section>
    </main>
  )
}

export default LandingPage