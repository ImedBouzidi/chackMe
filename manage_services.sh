#!/bin/bash

# CheckMe Services Management Script
# Manages: chess-web (Angular dev server), stockfish engine, etc.

PROJECT_ROOT="/home/imed/Documents/projects/CheckMe"
APPS_DIR="$PROJECT_ROOT/apps"
CHESS_WEB_DIR="$APPS_DIR/chess-web"
STOCKFISH_DIR="$APPS_DIR/stockfish"

PID_DIR="/tmp/checkme-services"
mkdir -p "$PID_DIR"

case "$1" in
    start)
        echo "Starting CheckMe services..."
        
        # Start Chess Web (Angular dev server)
        if [ -d "$CHESS_WEB_DIR" ] && [ -f "$CHESS_WEB_DIR/package.json" ]; then
            if [ -f "$PID_DIR/chess-web.pid" ] && kill -0 $(cat "$PID_DIR/chess-web.pid") 2>/dev/null; then
                echo "  chess-web already running (PID $(cat $PID_DIR/chess-web.pid))"
            else
                echo "  Starting chess-web (Angular dev server)..."
                cd "$CHESS_WEB_DIR"
                nohup npx ng serve --host 0.0.0.0 --port 4200 > "$PID_DIR/chess-web.log" 2>&1 &
                echo $! > "$PID_DIR/chess-web.pid"
                echo "    Started with PID $(cat $PID_DIR/chess-web.pid), log: $PID_DIR/chess-web.log"
            fi
        fi
        
        # Start Stockfish if needed (HTTP server or engine)
        if [ -f "$STOCKFISH_DIR/stockfish-linux-x86-64-universal" ] || [ -f "$HOME/apps/stockfish/stockfish" ]; then
            if [ -f "$PID_DIR/stockfish.pid" ] && kill -0 $(cat "$PID_DIR/stockfish.pid") 2>/dev/null; then
                echo "  stockfish already running (PID $(cat $PID_DIR/stockfish.pid))"
            else
                echo "  Stockfish binary found (UCI engine, usually used by web app via WASM/worker)"
                echo "    No persistent daemon needed for web worker mode"
            fi
        fi
        
        echo "Services started. Use 'status' to check."
        ;;
        
    stop)
        echo "Stopping CheckMe services..."
        
        # Stop Chess Web
        if [ -f "$PID_DIR/chess-web.pid" ]; then
            PID=$(cat "$PID_DIR/chess-web.pid")
            if kill -0 $PID 2>/dev/null; then
                echo "  Stopping chess-web (PID $PID)..."
                kill $PID
                sleep 2
                kill -9 $PID 2>/dev/null
                rm "$PID_DIR/chess-web.pid"
                echo "    Stopped"
            else
                echo "  chess-web not running"
                rm "$PID_DIR/chess-web.pid"
            fi
        else
            echo "  chess-web not running"
        fi
        
        # Stop any remaining stockfish processes
        STOCKFISH_PIDS=$(pgrep -f "stockfish.*linux" 2>/dev/null)
        if [ -n "$STOCKFISH_PIDS" ]; then
            echo "  Stopping stockfish processes..."
            for p in $STOCKFISH_PIDS; do
                kill $p 2>/dev/null
            done
            echo "    Stopped"
        else
            echo "  stockfish not running"
        fi
        
        echo "Services stopped."
        ;;
        
    status)
        echo "CheckMe Services Status:"
        echo "========================"
        
        # Check Chess Web
        if [ -f "$PID_DIR/chess-web.pid" ] && kill -0 $(cat "$PID_DIR/chess-web.pid") 2>/dev/null; then
            PID=$(cat "$PID_DIR/chess-web.pid")
            echo "  chess-web (Angular): RUNNING (PID $PID) - http://localhost:4200"
        else
            echo "  chess-web (Angular): STOPPED"
            [ -f "$PID_DIR/chess-web.pid" ] && rm "$PID_DIR/chess-web.pid"
        fi
        
        # Check Stockfish
        STOCKFISH_PIDS=$(pgrep -a "stockfish" 2>/dev/null | grep -v grep)
        if [ -n "$STOCKFISH_PIDS" ]; then
            echo "  stockfish: RUNNING"
            echo "$STOCKFISH_PIDS" | sed 's/^/    /'
        else
            echo "  stockfish: STOPPED (uses WASM worker in browser if needed)"
        fi
        
        echo ""
        echo "Logs: $PID_DIR/"
        ;;
        
    restart)
        $0 stop
        sleep 2
        $0 start
        ;;
        
    logs)
        if [ "$2" = "chess-web" ]; then
            tail -f "$PID_DIR/chess-web.log"
        elif [ "$2" = "all" ] || [ -z "$2" ]; then
            for log in "$PID_DIR"/*.log; do
                [ -f "$log" ] && echo "=== $(basename $log) ===" && tail -20 "$log" && echo
            done
        else
            echo "Usage: $0 logs [chess-web|all]"
        fi
        ;;
        
    *)
        echo "Usage: $0 {start|stop|status|restart|logs [chess-web|all]}"
        echo ""
        echo "Manage CheckMe services:"
        echo "  start   - Start all services"
        echo "  stop    - Stop all services"  
        echo "  status  - Show service status"
        echo "  restart - Restart all services"
        echo "  logs    - View logs"
        exit 1
        ;;
esac

exit 0
