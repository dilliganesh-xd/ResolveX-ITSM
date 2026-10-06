# -------- Build Stage --------
# Use Node.js 20 to build the React application
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

RUN npm run build


# -------- Runtime Stage --------
# Use the official unprivileged Nginx image, which runs as a non-root user and
# therefore listens on port 8080 instead of 80
FROM nginxinc/nginx-unprivileged:alpine

# Update Alpine packages to their latest security-fixed versions
USER root
RUN apk upgrade --no-cache

# Copy the React production build into Nginx's web root
COPY --from=build /app/dist /usr/share/nginx/html

# Use custom Nginx configuration for React routing and backend API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Drop back to the image's unprivileged nginx user (UID 101)
USER 101

EXPOSE 8080
