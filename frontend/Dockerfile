# -------- Build Stage --------
# Use Node.js 20 to build the React application
FROM node:20-alpine AS build

# Set the working directory inside the build container
WORKDIR /app

# Copy package files first to take advantage of Docker layer caching
COPY package*.json ./

# Install the exact dependencies defined in package-lock.json
RUN npm ci

# Copy the React application source code
COPY . .

# Build the React application for production
RUN npm run build


# -------- Runtime Stage --------
# Use lightweight Nginx to serve the production React application
FROM nginx:alpine

# Copy the React production build into Nginx's web root
COPY --from=build /app/dist /usr/share/nginx/html

# Use custom Nginx configuration for React routing and backend API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf