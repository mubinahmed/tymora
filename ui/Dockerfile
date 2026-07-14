# Multi-stage build for the UniTime Angular front-end.
# Stage 1: build the production bundle. Stage 2: serve it with nginx, which
# also reverse-proxies backend calls to the unitime-web container so the browser
# sees a single origin (the JSESSIONID session cookie then works for the facade).

FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Angular CLI outputs a browser/ subfolder under dist/<project>.
COPY --from=build /app/dist/ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
