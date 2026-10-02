# Artista

Artista is an Android application designed for both regular and occasional art lovers who want to discover artworks and exhibitions that match their interests, especially when exploring a new city.

---

## Overview & Pitch

Artista is designed for both regular and occasional art lovers who are visiting a new city and do not know where to find artworks that match their interests. Today, discovering whether a specific artist is represented in a city often requires searching through multiple museum websites, exhibition pages, or event platforms. For example, Emma, a 23-year-old art enthusiast visiting a new city, may want to know whether any works from her favorite artist are currently displayed nearby, but finding this information can be time-consuming and fragmented. Artista solves this problem by helping users locate artworks geographically and discover where they can see them, whether in a museum, gallery, or temporary exhibition. Users can search for specific artists or artworks, access information such as creation history, year, and materials used, and receive notifications when works from artists they follow are exhibited nearby. The app also provides an overview of upcoming exhibitions and art events in the area.

---

## Architecture & Split-App Model

Split-app model: We will use the WikiArt API to retrieve information about artworks and artists. Since we are currently having some issues setting it up, Europeana APIs will be used as a backup solution. As discussed with the coaches, if we face a major technical issue with the API, we can also use a mock API to simulate the expected data. The API will mainly be used for permanent artworks and collections, while temporary exhibitions and events will rely on user-generated content, only available to verified users to improve the reliability of the information. In addition, we will use Firebase as our public cloud service to store and synchronize user-related data such as user accounts, favorite artists and artworks, saved exhibitions, UGC submissions and notification preferences, allowing users to access their personalized data across different sessions and devices.

---

## Multi-User Support & Community

Users create an account and list the artists they are interested in. They can receive notifications about works by those artists based on their location and proximity. Their account also allows them to track their progress by seeing which artworks and artists they have already visited, together with key information such as the creation history, year, and materials used. Artista also includes multi-user interactions. Users can create a profile, follow other users with similar artistic interests, and discover what artworks or exhibitions they have visited. They can also comment on posts or shared artworks, exchange recommendations, and interact with a community of people who share similar interests in art.

---

## Sensor Integration: GPS

GPS is a core part of Artista because the app is designed around the geographical discovery of art. The user’s location is used to identify and prioritize nearby artworks, museums, galleries, and upcoming art events, so the content displayed by the app changes depending on where the user is. GPS is also used when users contribute to the platform. If an artwork is not yet listed in Artista, a user can add it and use their current GPS location to register where the artwork can be found. This helps keep the app’s geographical database accurate and up to date.

---

## Offline Mode

In offline mode, users can still access the artworks they have previously viewed, liked, or saved, including their main details such as the artist, title, year, and description. They can also browse their personal collection and previously saved exhibitions or places. Without an internet connection, users cannot search for new artworks, add new artworks to the platform, or receive updated information about exhibitions and events. Actions performed offline, such as liking or saving an already available artwork, can be synchronized with Firebase once the connection is restored.

---

## App Mockup

The app mockup is designed on [Figma](https://www.figma.com/files/folder/662626613)

---

## Team Members

- Alex Estella ([@IJJA3141](alex.estella.cj@gmail.com))
- Felix Burchardt ([@5kyPhy](felix.burchardt@hotmail.com))
- Kaio Freitas Pereira Nascimento ([@krfpn](kr.fpn@outlook.com))
- Maksim Romanov ([@hixeum](hixeum@gmail.com))
- Patrick Mcdaniel ([@patrickmcdan](webaccounts@tuta.io))
- Timothee Guitard ([@Timz3rr](timothee.guitard@epfl.ch))
- Timothy Byron-Exarcos ([@timo-by](timothy.byron-exarcos@epfl.ch))

---

## Coaches

- Alexis Poudens ([@AlexisPDS]())
- Rania Hida ([@Rania5724]())
