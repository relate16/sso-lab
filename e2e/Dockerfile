FROM mcr.microsoft.com/playwright:v1.62.0-noble

WORKDIR /tests
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts
COPY playwright.config.ts ./
COPY tests ./tests
RUN mkdir -p /tests/test-results && chown -R pwuser:pwuser /tests

USER pwuser
ENTRYPOINT ["npm", "test", "--"]
