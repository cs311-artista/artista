# Artista

Artista is an Android application designed for both regular and occasional art lovers who want to discover artworks and exhibitions that match their interests, especially when exploring a new city.

---

## Overview & Pitch

Artista is designed for both regular and occasional art lovers who are visiting
a new city and do not know where to find artworks that match their interests.
Today, discovering whether a specific artist is represented in a city often requires searching through multiple museum websites.

For example, Emma, a 23-year-old art enthusiast visiting a new city, may
want to know whether any works from her favorite artist are currently displayed
nearby, but finding this information can be time-consuming and fragmented.

Artista solves this problem by helping users locate artworks geographically
and discover where they can see them, whether in a museum or gallery. Users can search for specific artists or artworks, access information such as creation history, year, and materials used, and receive notifications when works from artists they follow are exhibited nearby.

---

## Architecture & Split-App Model

The Wikidata Action API will be used for retrieving individual entities and their properties, such as artwork titles, artists and locations. The Wikidata SPARQL endpoint will complement the Action API by allowing more complex queries, such as discovering artworks by a particular artist, finding artworks located in a specific museum or filtering artworks according to multiple criteria.

---

## Multi-User Support & Community

Users can sign-in using Google through Firebase Authentication. They can
receive notifications about works by those artists based on their location and
proximity. Their account also allows them to track their progress by seeing
which artworks they have already visited, together with key information such as the creation history, year, and materials used.

---

## Sensor Integration: GPS

GPS is a core part of Artista because the app is designed around the geographical discovery of art. The user’s location is used to identify and prioritize nearby artworks, museums and galleries so the content displayed by the app changes depending on where the user is.

---

## Offline Mode

In offline mode, users can still access the artworks they have previously saved including their main details such as the artist, title, year, and
description. They can also browse their personal collection and previously saved exhibitions or places.
Without an internet connection, users cannot search for new artworks but actions performed offline, such as saving an already available artwork, can be synchronized with Firebase once the connection is restored.

---

## App Mockup

The app mockup is designed on [Figma](https://www.figma.com/design/KqbZRf6ksgbnecninyG9DO/Test-Figma-Tim?node-id=0-1&t=YUptO7e5b8sIyKuj-1).

---

## Team Members

- Alex Estella ([@IJJA3141](https://github.com/IJJA3141))
- Felix Burchardt ([@5kyPhy](https://github.com/5kyPhy))
- Kaio Freitas Pereira Nascimento ([@krfpn](https://github.com/krfpn))
- Maksim Romanov ([@hixeum](https://github.com/hixeum))
- Patrick McDaniel ([@patrickmcdan](https://github.com/patrickmcdan))
- Timothee Guitard ([@Timz3rr](https://github.com/Timz3rr))
- Timothy Byron-Exarcos ([@timo-by](https://github.com/timo-by))

---

## Coaches

- Alexis Poudens ([@AlexisPDS](https://github.com/AlexisPDS))
- Rania Hida ([@Rania5724](https://github.com/Rania5724))
