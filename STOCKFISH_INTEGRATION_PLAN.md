# Stockfish Integration Plan for CheckMe

## Current State
- Angular 19 app (chess-web) with cm-chessboard, chess.js
- Stockfish engine downloaded to `public/assets/stockfish/` (stockfish-19-single.js/wasm)
- Desktop Stockfish binary also at `~/apps/stockfish/`
- Features: Play, Puzzles, Openings, Learn, Game Review (Analysis), Game component
- No existing engine/Stockfish service

## Goals
1. Add a reusable Stockfish engine service for local play vs computer
2. Integrate engine into "Play vs Computer" flow  
3. Add game analysis functionality (evaluate positions, best moves, move classifications)
4. Support configurable engine strength/skill level

## Architecture Options

### Option 1: Web Worker with Stockfish.js (Recommended for Browser)
**Pros:**
- Runs in browser, no backend needed
- Stockfish.js files already in public/assets/stockfish/
- Offloads computation from UI thread
- Works in production build

**Cons:**
- Limited by browser performance
- WASM files are ~95MB (large but acceptable)
- Need to handle worker lifecycle

**Implementation:**
```typescript
// engine.service.ts
- Use Worker or new Worker('assets/stockfish/stockfish-19-single.js')
- Communicate via UCI protocol (postMessage/onmessage)
- Methods: setPosition(fen), getBestMove(), evaluate(), stop()
- Observable streams for evaluation info
```

### Option 2: Use stockfish.wasm directly with Emscripten module
Also viable, stockfish.js wrapper handles this.

### Option 3: Node backend with desktop binary
Would need a backend server - none exists currently.

**Recommendation: Go with Option 1 (Web Worker + Stockfish.js)**

## Implementation Plan

### Phase 1: Create Engine Service (Core)
1. Create `src/app/core/services/stockfish-engine.service.ts`
   - Initialize worker pointing to `assets/stockfish/stockfish-19-single.js`
   - UCI protocol implementation
   - Methods: init(), setFEN(fen), go(depth/time/multiPV), stop(), setSkillLevel(level), setOption(name, value)
   - Event emitters for bestmove, info (score, pv, depth)
   - Handle worker messages

2. Add types/interfaces for engine responses
   - `src/app/core/models/engine.models.ts` for EngineInfo, BestMove, etc.

### Phase 2: Play vs Computer
1. Enhance `play.component.ts` - add bot difficulty selector (ELO/skill level 0-20)
2. Create/update game play flow for vs-computer
   - Could use `game/:id` route or create dedicated vs-bot component
   - Or extend `game.component.ts` to support vs-computer mode
3. Flow: User selects bot difficulty -> starts game vs engine -> engine responds to user moves

### Phase 3: Game Analysis
1. Enhance `analysis.component.ts` 
   - Upload PGN or paste game
   - Parse with chess.js
   - Run engine analysis move by move
   - Show evaluation, best move, move classification (blunder/mistake/inaccuracy/good/excellent)
   - Add evaluation bar/graph component
2. Create `analysis.service.ts` for move classification logic

### Phase 4: UI Components
1. Create engine status indicator
2. Add evaluation bar component  
3. Add analysis panel showing engine lines
4. Add "Play vs Bot" modal/dialog in play component

## File Structure
```
src/app/
├── core/
│   ├── models/
│   │   └── engine.models.ts (new)
│   └── services/
│       ├── stockfish-engine.service.ts (new)
│       └── analysis.service.ts (new)
├── features/
│   ├── play/
│   │   └── play-vs-bot-dialog/ (new component)
│   ├── game/
│   │   └── game.component.ts (enhance)
│   └── analysis/
│       ├── analysis.component.ts (enhance)
│       └── components/ (new)
│           ├── evaluation-bar/
│           └── move-analysis/
└── shared/
    └── components/
```

## Technical Details

### UCI Protocol Basics
```
Send: "uci"
Receive: "uciok"

Send: "setoption name Skill Level value 10"
Send: "position fen <fen>"
Send: "go depth 20" or "go movetime 1000"
Receive: "info depth 20 score cp 50 pv e2e4..."
Receive: "bestmove e2e4 ponder e7e5"
```

### Stockfish.js Usage
```javascript
const engine = new Worker('assets/stockfish/stockfish-19-single.js');
engine.postMessage('uci');
engine.onmessage = (e) => { /* parse e.data */ };
```

Note: stockfish-19-single.js is designed to run as worker with WASM.

### Skill Levels
- Skill Level 0-20 (lower = weaker)
- Can also limit depth/time for different strengths
- Or use UCI_Elo if supported

## Considerations
- Stockfish WASM is large (~95MB) - acceptable for local use
- Need to copy assets correctly (already in public/, will be in dist/)
- Worker path needs to be correct relative to base href
- For Angular production build, assets are copied as-is
- Consider lazy loading engine only when needed
- Add loading state while engine initializes

## Quick Start Implementation
1. Create the engine service
2. Test in browser console first
3. Integrate into play vs bot
4. Add basic analysis

Let me start implementing the engine service.
