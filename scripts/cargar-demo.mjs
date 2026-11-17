// Carga datos de demostración en DEVUTEC usando la misma API que la web y la app.
// Crea clientes (con las redes de su negocio), freelancers (con su perfil), proyectos variados,
// postulaciones y algunos contratos en distintos estados, con reseñas en los terminados.
//
// Uso (por defecto va al backend local, para no llenar la base de producción sin querer):
//   DEMO_PASSWORD='...' node scripts/cargar-demo.mjs
//   API_URL=https://devutec.duckdns.org/api/v1 DEMO_PASSWORD='...' node scripts/cargar-demo.mjs   -> producción
//
// Se puede correr más de una vez: si una cuenta, proyecto o postulación ya existe, la usa y sigue.

const API_URL = (process.env.API_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')
const PASSWORD = process.env.DEMO_PASSWORD
const MAILBOX = 'devutec.notificaciones' // todas las cuentas son alias de este Gmail: los correos llegan ahí

if (!PASSWORD || !/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,100}$/.test(PASSWORD)) {
  console.error('Falta DEMO_PASSWORD o no cumple la regla: 8+ caracteres con mayúscula, minúscula, número y símbolo.')
  process.exit(1)
}

const email = (alias) => `${MAILBOX}+${alias}@gmail.com`
const inDays = (days) => {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

// ---------- Datos ----------

const CLIENTS = [
  {
    alias: 'demo-cliente-pasteleria',
    nombre: 'Lucía Fernández',
    redes: { instagram: '@dulcelucia.pe', facebook: 'facebook.com/dulcelucia', tiktok: '@dulcelucia', sitioWeb: '' },
  },
  {
    alias: 'demo-cliente-cafe',
    nombre: 'Martín Quispe',
    redes: { instagram: '@cafetostadolima', facebook: '', tiktok: '', sitioWeb: 'cafetostado.pe' },
  },
  {
    alias: 'demo-cliente-yoga',
    nombre: 'Andrea Salazar',
    redes: { instagram: '@shantiyogastudio', facebook: 'facebook.com/shantiyoga', tiktok: '', sitioWeb: '' },
  },
  {
    alias: 'demo-cliente-ferreteria',
    nombre: 'Jorge Huamán',
    redes: { instagram: '', facebook: 'facebook.com/ferreteriahuaman', tiktok: '', sitioWeb: '' },
  },
]

const FREELANCERS = [
  {
    alias: 'demo-free-diseno',
    nombre: 'Valeria Ríos',
    profile: {
      universidad: 'UTEC',
      ciclo: 7,
      egresado: false,
      tarifaHora: 35,
      presentacion:
        'Diseño identidades y piezas para redes de negocios pequeños. Me gusta entender a quién le vendes antes de abrir Figma, y entrego todo ordenado para que lo puedas usar sin mí.',
    },
  },
  {
    alias: 'demo-free-web',
    nombre: 'Diego Paredes',
    profile: {
      universidad: 'UTEC',
      ciclo: 9,
      egresado: false,
      tarifaHora: 45,
      presentacion:
        'Hago páginas web y tiendas online con React y Shopify. Trabajo por entregas cortas para que veas avances cada semana, y te explico cómo actualizar tu web tú misma.',
    },
  },
  {
    alias: 'demo-free-datos',
    nombre: 'Camila Torres',
    profile: {
      universidad: 'UTEC',
      egresado: true,
      tarifaHora: 50,
      presentacion:
        'Egresada de Ciencia de la Computación. Ordeno datos de ventas en Excel y Power BI para que sepas qué productos te dejan más y cuándo reponer stock.',
    },
  },
  {
    alias: 'demo-free-video',
    nombre: 'Renzo Castillo',
    profile: {
      universidad: 'UTEC',
      ciclo: 5,
      egresado: false,
      tarifaHora: 30,
      presentacion:
        'Grabo y edito reels y videos cortos para TikTok e Instagram. Llevo mi equipo, propongo el guion y te entrego los cortes en formato vertical listos para subir.',
    },
  },
  {
    alias: 'demo-free-movil',
    nombre: 'Sofía Mendoza',
    profile: {
      universidad: 'UTEC',
      ciclo: 8,
      egresado: false,
      tarifaHora: 40,
      presentacion:
        'Desarrollo apps con React Native y backends con Spring Boot. Me enfoco en que funcionen bien en celulares sencillos y con internet lento.',
    },
  },
]

// key sirve para referirse al proyecto en las postulaciones; no se manda al backend
const PROJECTS = [
  {
    key: 'logo-pasteleria',
    client: 'demo-cliente-pasteleria',
    title: 'Logo y paleta de colores para pastelería',
    description:
      'Tengo una pastelería en Surco con 3 años y quiero renovar la marca. Necesito logo, paleta de colores, tipografías y una plantilla para los posts de Instagram.',
    budget: 650,
    days: 20,
    location: 'Surco',
    skills: ['Branding', 'Diseño gráfico', 'Figma'],
  },
  {
    key: 'reels-pasteleria',
    client: 'demo-cliente-pasteleria',
    title: 'Reels de tortas para Instagram y TikTok',
    description:
      'Busco a alguien que grabe y edite 6 reels cortos mostrando cómo decoramos las tortas. Tenemos buena luz en el local; tú propones las ideas.',
    budget: 480,
    days: 15,
    location: 'Surco',
    skills: ['Edición de video', 'Redes sociales'],
  },
  {
    key: 'web-cafe',
    client: 'demo-cliente-cafe',
    title: 'Página web con carta y reservas para cafetería',
    description:
      'Queremos una web sencilla con la carta, fotos del local, horario, ubicación en el mapa y un formulario para reservar mesas para grupos.',
    budget: 1200,
    days: 30,
    location: 'Barranco',
    skills: ['React', 'HTML y CSS', 'Diseño UX/UI'],
  },
  {
    key: 'fotos-cafe',
    client: 'demo-cliente-cafe',
    title: 'Fotos de producto de nuestros cafés de especialidad',
    description:
      'Necesitamos 20 fotos de producto de nuestras bolsas de café y bebidas para la web y Rappi. Fondo claro y algunas con ambiente del local.',
    budget: 350,
    days: 10,
    location: 'Barranco',
    skills: ['Fotografía de producto'],
  },
  {
    key: 'dashboard-cafe',
    client: 'demo-cliente-cafe',
    title: 'Dashboard de ventas en Power BI',
    description:
      'Exportamos las ventas del POS a Excel cada semana. Queremos un dashboard que muestre qué se vende más por día y hora, para planificar mejor el stock.',
    budget: 900,
    days: 25,
    location: 'Remoto',
    skills: ['Power BI', 'Excel', 'Análisis de datos'],
  },
  {
    key: 'app-yoga',
    client: 'demo-cliente-yoga',
    title: 'App para reservar clases de yoga',
    description:
      'Tenemos 2 salas y unas 15 clases por semana. Buscamos una app sencilla donde las alumnas vean el horario, reserven su lugar y reciban un recordatorio.',
    budget: 2500,
    days: 45,
    location: 'Miraflores',
    skills: ['React Native', 'Spring Boot', 'Diseño UX/UI'],
  },
  {
    key: 'contenido-yoga',
    client: 'demo-cliente-yoga',
    title: 'Plan de contenido para Instagram (1 mes)',
    description:
      'Necesito un calendario de publicaciones para un mes, con los textos y el diseño de 12 posts e historias para anunciar los talleres de fin de semana.',
    budget: 550,
    days: 14,
    location: 'Remoto',
    skills: ['Redes sociales', 'Marketing digital', 'Redacción'],
  },
  {
    key: 'tienda-ferreteria',
    client: 'demo-cliente-ferreteria',
    title: 'Tienda online para ferretería de barrio',
    description:
      'Queremos vender por internet los productos más pedidos (unos 150). Necesitamos la tienda, pagos con tarjeta y Yape, y que podamos subir productos nosotros.',
    budget: 1800,
    days: 40,
    location: 'San Miguel',
    skills: ['Shopify', 'E-commerce'],
  },
  {
    key: 'inventario-ferreteria',
    client: 'demo-cliente-ferreteria',
    title: 'Ordenar el inventario en Excel con alertas de stock',
    description:
      'Tenemos el inventario en varias hojas desordenadas. Queremos un solo Excel con fórmulas que avise cuando un producto esté por agotarse.',
    budget: 400,
    days: 12,
    location: 'San Miguel',
    skills: ['Excel', 'Automatización'],
  },
  {
    key: 'landing-ferreteria',
    client: 'demo-cliente-ferreteria',
    title: 'Landing page para campaña de verano',
    description:
      'Una página de una sola sección con las ofertas de verano (ventiladores, pintura, piscinas) y un botón para escribirnos por WhatsApp.',
    budget: 500,
    days: 18,
    location: 'Remoto',
    skills: ['HTML y CSS', 'WordPress', 'SEO'],
  },
]

// Postulaciones. outcome dice hasta dónde avanza: 'pending' (queda pendiente),
// 'accepted' (contrato en curso), 'delivered' (entregado, el cliente debe revisar) o 'confirmed' (pago liberado).
// Al aceptar una, el backend rechaza solo las demás pendientes del mismo proyecto.
const APPLICATIONS = [
  // Contrato terminado con reseñas
  { project: 'logo-pasteleria', free: 'demo-free-diseno', price: 650, outcome: 'confirmed',
    message: 'Hola Lucía, me encantaría renovar la marca de tu pastelería. Te propongo 3 rutas de logo y luego afinamos la que más te guste.',
    reviews: { client: [5, 'Valeria entendió el estilo que buscaba desde la primera reunión. Entregó todo a tiempo y muy ordenado.'],
               free: [5, 'Lucía tenía muy claro lo que quería y respondía rápido. Muy buena experiencia.'] } },
  { project: 'logo-pasteleria', free: 'demo-free-web', price: 700,
    message: 'Puedo ayudarte con la marca y luego adaptarla a una web sencilla si más adelante la necesitas.' },

  { project: 'fotos-cafe', free: 'demo-free-video', price: 350, outcome: 'confirmed',
    message: 'Tengo luces y fondos para fotos de producto. Puedo ir un día por la mañana cuando el local tenga buena luz natural.',
    reviews: { client: [4, 'Muy buenas fotos y puntual. Tardó un par de días más en la edición, pero el resultado valió la pena.'],
               free: [5, 'Martín me dio todas las facilidades en el local. Proyecto muy bonito.'] } },

  // Contrato entregado: el cliente tiene que revisar
  { project: 'dashboard-cafe', free: 'demo-free-datos', price: 900, outcome: 'delivered',
    message: 'He armado dashboards de ventas para restaurantes. Primero reviso tus exportaciones y te muestro un borrador en 5 días.' },
  { project: 'dashboard-cafe', free: 'demo-free-movil', price: 950,
    message: 'Puedo conectar tu Excel a Power BI y dejar el reporte actualizándose solo cada semana.' },

  // Contratos en curso
  { project: 'app-yoga', free: 'demo-free-movil', price: 2400, outcome: 'accepted',
    message: 'Hice una app de reservas para un gimnasio el ciclo pasado. Te propongo empezar por el horario y las reservas, y luego los recordatorios.' },
  { project: 'app-yoga', free: 'demo-free-web', price: 2600,
    message: 'Puedo hacer la app y además una versión web para quienes reservan desde la computadora.' },
  { project: 'tienda-ferreteria', free: 'demo-free-web', price: 1750, outcome: 'accepted',
    message: 'Con Shopify puedes subir productos tú mismo y aceptar Yape y tarjeta. Te dejo una guía en video al terminar.' },

  // Proyectos abiertos con postulaciones pendientes
  { project: 'web-cafe', free: 'demo-free-web', price: 1150,
    message: 'Me gusta mucho tu cafetería. Te propongo una web rápida con la carta editable y reservas que te lleguen por correo.' },
  { project: 'web-cafe', free: 'demo-free-diseno', price: 1300,
    message: 'Puedo diseñar la web en Figma con fotos del local y trabajar con un compañero para programarla.' },
  { project: 'web-cafe', free: 'demo-free-movil', price: 1200,
    message: 'Hago la web con React y un pequeño backend para las reservas, con aviso por WhatsApp para ti.' },
  { project: 'reels-pasteleria', free: 'demo-free-video', price: 480,
    message: 'Te propongo grabar los 6 reels en una sola mañana y entregarlos editados con música en tendencia esa misma semana.' },
  { project: 'contenido-yoga', free: 'demo-free-diseno', price: 550,
    message: 'Armo el calendario del mes con los textos y diseño los posts con la misma línea visual para que tu feed se vea ordenado.' },
  { project: 'contenido-yoga', free: 'demo-free-video', price: 600,
    message: 'Además de los posts, puedo grabar 2 videos cortos de una clase para anunciar los talleres.' },
  { project: 'inventario-ferreteria', free: 'demo-free-datos', price: 380,
    message: 'Unifico tus hojas en un solo Excel con formato condicional que te avise en rojo cuando quede poco stock.' },
]

// ---------- API ----------

async function api(method, path, { token, body, ok = [] } = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok && !ok.includes(response.status)) {
    const message = data?.message ?? text
    throw new Error(`${method} ${path} → ${response.status}: ${message}`)
  }
  return { status: response.status, data }
}

const list = (data) => (Array.isArray(data) ? data : (data?.content ?? []))

async function account({ alias, nombre }, rol) {
  const credentials = { email: email(alias), password: PASSWORD }
  const created = await api('POST', '/auth/register', {
    body: { nombre, ...credentials, rol, acceptedTerms: true },
    ok: [409],
  })
  const session = created.status === 409 ? (await api('POST', '/auth/login', { body: credentials })).data : created.data
  const me = (await api('GET', '/users/me', { token: session.accessToken })).data
  console.log(`  ${created.status === 409 ? '·' : '+'} ${rol === 'CLIENT' ? 'cliente' : 'freelancer'} ${nombre}`)
  return { token: session.accessToken, id: me.id, nombre }
}

// Si falla un paso, se avisa y se sigue con lo demás
async function step(label, fn) {
  try {
    return await fn()
  } catch (error) {
    console.warn(`  ! ${label}: ${error.message}`)
    return null
  }
}

// ---------- Carga ----------

async function main() {
  console.log(`Cargando datos de demo en ${API_URL}\n`)

  console.log('Cuentas')
  const users = {}
  for (const client of CLIENTS) {
    users[client.alias] = await account(client, 'CLIENT')
    await step(`redes de ${client.nombre}`, () =>
      api('PUT', '/users/me/links', { token: users[client.alias].token, body: client.redes }),
    )
  }
  for (const freelancer of FREELANCERS) {
    users[freelancer.alias] = await account(freelancer, 'FREELANCER')
    await step(`perfil de ${freelancer.nombre}`, () =>
      api('PUT', '/freelancers/me', { token: users[freelancer.alias].token, body: freelancer.profile }),
    )
  }

  // Las habilidades se piden con sesión: se usa la de un cliente
  const skills = (await api('GET', '/skills', { token: users[CLIENTS[0].alias].token })).data
  const skillIds = (names) =>
    names.map((name) => skills.find((skill) => skill.name.toLowerCase() === name.toLowerCase())?.id).filter(Boolean)

  console.log('\nProyectos')
  const projects = {}
  for (const project of PROJECTS) {
    const owner = users[project.client]
    const mine = list((await api('GET', '/projects/me?size=100', { token: owner.token })).data)
    const existing = mine.find((item) => item.title === project.title)
    if (existing) {
      projects[project.key] = existing
      console.log(`  · ${project.title}`)
      continue
    }
    const created = await step(project.title, () =>
      api('POST', '/projects', {
        token: owner.token,
        body: {
          title: project.title,
          description: project.description,
          budget: project.budget,
          deadline: inDays(project.days),
          location: project.location,
          skillIds: skillIds(project.skills),
        },
      }),
    )
    if (created) {
      projects[project.key] = created.data
      console.log(`  + ${project.title}`)
    }
  }

  console.log('\nPostulaciones')
  for (const item of APPLICATIONS) {
    const project = projects[item.project]
    if (!project) continue
    const free = users[item.free]
    const result = await step(`${free.nombre} → ${project.title}`, () =>
      api('POST', '/applications', {
        token: free.token,
        body: { projectId: project.id, message: item.message, proposedPrice: item.price },
        ok: [409, 422],
      }),
    )
    if (result) console.log(`  ${result.status >= 400 ? '·' : '+'} ${free.nombre} → ${project.title}`)
  }

  console.log('\nContratos')
  for (const item of APPLICATIONS.filter((application) => application.outcome)) {
    const project = projects[item.project]
    if (!project) continue
    const free = users[item.free]
    const client = users[PROJECTS.find((p) => p.key === item.project).client]
    const label = `${project.title} (${free.nombre})`

    // Aceptar la postulación si sigue pendiente
    const applications = (await api('GET', `/applications?projectId=${project.id}`, { token: client.token })).data
    const application = applications.find((a) => a.freelancerUserId === free.id)
    if (application?.status === 'PENDING') {
      await step(`aceptar ${label}`, () => api('PATCH', `/applications/${application.id}/accept`, { token: client.token }))
    }

    const findContract = async (token) =>
      list((await api('GET', '/contracts/me', { token })).data).find((c) => c.projectId === project.id)

    let contract = await findContract(free.token)
    if (!contract) {
      console.warn(`  ! ${label}: no se encontró el contrato`)
      continue
    }

    if (['delivered', 'confirmed'].includes(item.outcome) && contract.status === 'IN_PROGRESS') {
      await step(`entregar ${label}`, () => api('PATCH', `/contracts/${contract.id}/deliver`, { token: free.token }))
      contract = await findContract(free.token)
    }
    if (item.outcome === 'confirmed' && contract.status === 'DELIVERED') {
      // Si el backend exige pagar con tarjeta antes de liberar (STRIPE_REQUIRE_PAYMENT, activo en producción), este paso
      // falla y el contrato queda entregado: se paga con la tarjeta de prueba desde la web y se libera ahí
      await step(`liberar el pago de ${label}`, () =>
        api('PATCH', `/contracts/${contract.id}/confirm`, { token: client.token }),
      )
      contract = await findContract(free.token)
    }
    if (item.reviews && contract.status === 'CONFIRMED') {
      for (const [who, [rating, comment]] of Object.entries(item.reviews)) {
        const token = who === 'client' ? client.token : free.token
        await step(`reseña de ${label}`, () =>
          api('POST', `/contracts/${contract.id}/reviews`, { token, body: { rating, comment }, ok: [409, 422] }),
        )
      }
    }
    console.log(`  ${contract.status.padEnd(11)} ${label}`)
  }

  console.log('\nListo ✨ Todas las cuentas usan la contraseña de DEMO_PASSWORD.')
  console.log(`Correos: ${MAILBOX}+<alias>@gmail.com, por ejemplo ${email(CLIENTS[0].alias)}`)
}

main().catch((error) => {
  console.error(`\nSe detuvo: ${error.message}`)
  process.exit(1)
})
