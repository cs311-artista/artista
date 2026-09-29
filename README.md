# Artista

Artista is an Android application designed for both regular and occasional art lovers who want to discover artworks and exhibitions that match their interests, especially when exploring a new city.

---

## Overview & Pitch

Discovering whether a specific artist is exhibited in a city often requires searching through multiple museum websites, gallery listings, and event platforms. Finding this information can be fragmented and time-consuming.

**Artista** solves this by helping users locate artworks geographically:
- **Discover & Locate:** Find where specific artworks or artists are on display (museums, galleries, or temporary exhibitions).
- **Artwork Details:** Access key metadata such as creation history, year, and materials used.
- **Proximity Alerts:** Receive notifications when works by artists you follow are exhibited nearby.
- **Local Exhibitions:** Get an overview of upcoming art events and exhibitions in the area.

---

## Architecture & Split-App Model

The app leverages external APIs and a cloud backend to balance content delivery with personal data synchronization:
- **WikiArt API:** Serves as the visual art encyclopedia to fetch detailed information about artists, paintings, and art history.
- **Firebase:** Acts as the public cloud backend to synchronize personalized user data across sessions and devices:
  - User accounts and authentication
  - Favorite artists and saved artworks
  - Followed exhibitions and notification preferences

---

## Multi-User Support & Community

- **Personalized Tracking:** Users can build an account, list preferred artists, and track their art journey by logging which works and exhibitions they have visited.
- **Social Features:** Create a public profile, follow other art enthusiasts with similar tastes, view their visited exhibitions, comment on shared artworks, and exchange recommendations.

---

## Sensor Integration: GPS

Location services play a central role in the core user experience:
- **Proximity Discovery:** Prioritizes nearby artworks, museums, galleries, and events dynamically based on the user's current GPS coordinates.
- **Community Contributions:** When adding an unlisted artwork or exhibition, users can tag their real-time GPS location to keep the platform's geographical map accurate and updated.

---

## Offline Mode

Artista ensures core functionality remains accessible without an internet connection:
- **Available Offline:** Browse previously viewed, liked, or saved artworks, as well as saved exhibitions, personal collections, and cached artwork details.
- **Offline Actions & Sync:** Actions taken while offline (e.g., liking or saving cached items) are queued locally and automatically synchronized with Firebase once the network connection is restored.
- **Requires Connectivity:** Global search for new artworks, contributing new locations, and real-time event updates require an active internet connection.

---

## Team Members

- Alex Estella ([@IJJA3141](alex.estella.cj@gmail.com))
- Felix Burchardt ([@5kyPhy](felix.burchardt@hotmail.com))
- Kaio Freitas Pereira Nascimento ([@krfpn](kr.fpn@outlook.com))
- Maksim Romanov ([@hixeum](hixeum@gmail.com))
- Patrick Mcdaniel ([@patrickmcdan](webaccounts@tuta.io))
- Timothee Guitard ([@Timz3rr](timothee.guitard@epfl.ch))
- Timothy Byron-exarcos ([@]())

---

## Coaches

- Alexis Poudens ([@]())
- Rania Hida ([@]())
