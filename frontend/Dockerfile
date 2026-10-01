# -------- Build Stage --------
# Use Node.js 20 to build the React application
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build


# -------- Runtime Stage --------
# Use lightweight Nginx to serve the production React application
FROM nginx:alpine

# Update Alpine packages to their latest security-fixed versions
RUN apk upgrade --no-cache

# Copy the React production build into Nginx's web root
COPY --from=build /app/dist /usr/share/nginx/html

# Use custom Nginx configuration for React routing and backend API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf
