# Browser API - React Web UI

Modern React + TypeScript frontend for the Browser API workflows management.

## Tech Stack

- **React 18** - UI framework  
- **TypeScript** - Type safety  
- **Vite** - Build tool  
- **Tailwind CSS** - Styling  
- **Zustand** - State management  
- **STOMP** - WebSocket client

## Development

### Option 1: Integrated (Recommended - Production Mode)
Frontend is automatically built and served by Spring Boot:

```bash
# Build everything (Java + React)
mvn clean install

# Run Spring Boot (serves React at http://localhost:8080)
mvn spring-boot:run
```

The React app is built during Maven's generate-resources phase and copied to `target/classes/static`.

### Option 2: Separate Dev Servers (Development Mode - Hot Reload)
For faster frontend development with hot module replacement:

```bash
# Terminal 1: Run Spring Boot backend only
mvn spring-boot:run

# Terminal 2: Run Vite dev server with hot reload
cd src/main/web
npm install --no-bin-links  # First time only
npm run dev

# Visit http://localhost:3000
# API calls are proxied to Spring Boot on :8080
```

## How Maven Integration Works

When you run `mvn clean install`, the `frontend-maven-plugin` automatically:

1. ✅ Installs Node.js and npm (in target/ directory)
2. ✅ Runs `npm install --no-bin-links`
3. ✅ Runs `npm run build` (Vite builds to `dist/`)
4. ✅ Copies `dist/` to `target/classes/static`
5. ✅ Packages everything into a single JAR

**Result**: Single JAR deployment with both backend and frontend! 🎉

## Manual Development Commands

```bash
# Install dependencies
npm install --no-bin-links

# Run dev server (localhost:3000)
npm run dev

# Build for production (outputs to dist/)
npm run build

# Preview production build
npm run preview
```

## Project Structure

```
src/
├── components/     # React components
├── stores/         # Zustand state stores  
├── services/       # API services (fetch wrappers)
├── types/          # TypeScript type definitions
└── App.tsx         # Main application component
```

## Next Steps

- [ ] Add Shadcn/ui component library
- [ ] Implement workflow create/edit dialogs  
- [ ] Add workflow execution tracking UI
- [ ] Implement WebSocket real-time updates
- [ ] Add Playwright code generation templates
