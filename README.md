# DevUTEC — Plataforma freelance con pago en garantía para desarrolladores junior

**Curso:** CS 2031 Desarrollo Basado en Plataforma — UTEC, 2026-2

**Integrantes:**

| Nombre completo | Código | Usuario de GitHub |
|---|---|---|
| Alisson Luciana Breña Dávila | 202410064 | [@alissonbrena-dotcom](https://github.com/alissonbrena-dotcom) |
| Camila Gomez Arias | 202510093 | [@camilagomeza-lgtm](https://github.com/camilagomeza-lgtm) |
| Ariana Alejandra Isla Urbina | 202510309 | [@aisla9](https://github.com/aisla9), [@aaariana9](https://github.com/aaariana9) |
| Nicole Valeria Yucra Castro | 202510210 | [@ny216](https://github.com/ny216), [@nicoleyucra1](https://github.com/nicoleyucra1) |

**API desplegada (AWS EC2 + RDS):** `http://ec2-52-7-186-192.compute-1.amazonaws.com:8080`

---

## Índice

1. [Introducción](#introducción)
2. [Identificación del problema o necesidad](#identificación-del-problema-o-necesidad)
3. [Descripción de la solución](#descripción-de-la-solución)
4. [Modelo de entidades](#modelo-de-entidades)
5. [Manejo de errores](#manejo-de-errores)
6. [Medidas de seguridad implementadas](#medidas-de-seguridad-implementadas)
7. [Eventos y asincronía](#eventos-y-asincronía)
8. [GitHub & Management](#github--management)
9. [Guía de uso](#guía-de-uso)
10. [Conclusión](#conclusión)
11. [Apéndices](#apéndices)

---

## Introducción

### Contexto

Los estudiantes de Ciencias de la Computación terminan sus primeros ciclos con las competencias técnicas para construir software real, pero conseguir su primer trabajo pagado es difícil. En las plataformas freelance establecidas compiten contra desarrolladores senior con años de trayectoria, y en los canales informales (grupos de WhatsApp, recomendaciones) nada garantiza que el cliente pague al terminar el trabajo.

Del otro lado, los emprendedores y negocios pequeños necesitan proyectos de software puntuales —una web para su restaurante, un tablero de ventas— y no tienen forma sencilla de encontrar talento confiable a un precio accesible.

DevUTEC es el backend de una plataforma que conecta a ambos grupos con reglas claras y un pago protegido para las dos partes.

### Objetivos del proyecto

- Permitir que los clientes **publiquen proyectos** con presupuesto, plazo y habilidades requeridas, y que los freelancers **postulen** con una propuesta y un precio.
- Implementar un **pago en garantía (escrow)**: el dinero queda retenido al aceptar una postulación y solo se libera al freelancer cuando el cliente confirma la entrega; si el contrato se cancela, se reembolsa.
- Construir **reputación verificable** mediante reseñas entre cliente y freelancer al cerrar cada contrato.
- Ofrecer una **API REST segura** (JWT y roles), con notificaciones por correo asíncronas y desplegada en la nube.
