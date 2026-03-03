# Hello-Agent Onboarding Checklist

1. Build backend modules:
   - `mvn clean install`
2. Start backend service:
   - `mvn -pl Agent-Studio spring-boot:run`
3. Install frontend dependencies:
   - `cd frontend && npm install`
4. Start frontend dev server:
   - `cd frontend && npm run dev`
5. Verify skills index:
   - `POST /api/skills/reindex`
   - `GET /api/skills?q=demo`
