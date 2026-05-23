package com.teoe.wdl.server;

import com.sun.net.httpserver.HttpServer;
import com.teoe.wdl.DownloadManager;
import com.teoe.wdl.tracker.AutoScanner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WdlWebServer {
    private static HttpServer server;
    private static int port = 8080;

    public static void start(int targetPort) {
        if (server != null) {
            stop();
        }
        try {
            port = targetPort;
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            
            server.createContext("/", exchange -> {
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();
                
                if (method.equals("GET") && path.equals("/")) {
                    serveHtml(exchange);
                } else if (path.equals("/api/status")) {
                    serveStatus(exchange);
                } else if (path.equals("/api/map")) {
                    serveMap(exchange);
                } else if (path.equals("/api/map.jpg")) {
                    serveMapJpg(exchange);
                } else if (path.equals("/api/logs")) {
                    serveLogs(exchange);
                } else if (method.equals("POST") && path.equals("/api/seed")) {
                    handleSeed(exchange);
                } else if (method.equals("POST") && path.equals("/api/auto/start")) {
                    handleAutoStart(exchange);
                } else if (method.equals("POST") && path.equals("/api/auto/botprefix")) {
                    handleBotPrefix(exchange);
                } else if (method.equals("POST") && path.equals("/api/auto/fakeprefix")) {
                    handleFakePrefix(exchange);
                } else if (method.equals("POST") && path.equals("/api/config/update")) {
                    handleConfigUpdate(exchange);
                } else if (method.equals("POST") && path.equals("/api/auto/stop")) {
                    handleAutoStop(exchange);
                } else if (method.equals("GET") && path.equals("/api/download")) {
                    handleDownloadZip(exchange);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
                exchange.close();
            });
            
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("[TeoeWDL] Web server started on http://localhost:" + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("[TeoeWDL] Web server stopped.");
        }
    }

    private static void serveHtml(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>TeoeWDL Advanced Dashboard</title>\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --bg-color: #0b0f19;\n" +
                "            --panel-bg: #151a2a;\n" +
                "            --accent: #00e5ff;\n" +
                "            --text-main: #e2e8f0;\n" +
                "            --text-muted: #94a3b8;\n" +
                "            --danger: #ff4757;\n" +
                "            --success: #2ed573;\n" +
                "        }\n" +
                "        body {\n" +
                "            font-family: 'Consolas', 'Courier New', monospace;\n" +
                "            background-color: var(--bg-color);\n" +
                "            color: var(--text-main);\n" +
                "            margin: 0;\n" +
                "            padding: 20px;\n" +
                "            display: grid;\n" +
                "            grid-template-columns: 350px 1fr;\n" +
                "            gap: 20px;\n" +
                "            height: 100vh;\n" +
                "            box-sizing: border-box;\n" +
                "        }\n" +
                "        h1, h2 { margin-top: 0; color: var(--accent); text-transform: uppercase; letter-spacing: 2px; }\n" +
                "        .panel {\n" +
                "            background: var(--panel-bg);\n" +
                "            border: 1px solid rgba(0, 229, 255, 0.2);\n" +
                "            border-radius: 8px;\n" +
                "            padding: 20px;\n" +
                "            box-shadow: 0 4px 15px rgba(0, 0, 0, 0.5);\n" +
                "            display: flex; flex-direction: column; gap: 15px;\n" +
                "        }\n" +
                "        .stat-box {\n" +
                "            display: flex; justify-content: space-between; align-items: center;\n" +
                "            padding: 10px; background: rgba(255, 255, 255, 0.03);\n" +
                "            border-radius: 6px; border-left: 3px solid var(--accent);\n" +
                "        }\n" +
                "        .stat-label { color: var(--text-muted); font-size: 0.9em; }\n" +
                "        .stat-value { font-size: 1.2em; font-weight: bold; color: var(--accent); }\n" +
                "        .stat-value.active { color: var(--success); }\n" +
                "        .stat-value.inactive { color: var(--text-muted); }\n" +
                "        .stat-value.danger { color: var(--danger); }\n" +
                "        input[type=\"number\"], input[type=\"text\"] {\n" +
                "            background: rgba(0, 0, 0, 0.3); border: 1px solid rgba(0, 229, 255, 0.3);\n" +
                "            color: var(--text-main); padding: 8px 12px; border-radius: 4px;\n" +
                "            font-family: inherit; width: 100%; box-sizing: border-box; outline: none;\n" +
                "        }\n" +
                "        input:focus { border-color: var(--accent); }\n" +
                "        button {\n" +
                "            background: rgba(0, 229, 255, 0.1); color: var(--accent);\n" +
                "            border: 1px solid var(--accent); padding: 10px 15px;\n" +
                "            border-radius: 4px; cursor: pointer; text-transform: uppercase;\n" +
                "            transition: all 0.3s ease; width: 100%;\n" +
                "        }\n" +
                "        button:hover { background: var(--accent); color: var(--bg-color); }\n" +
                "        button.danger { border-color: var(--danger); color: var(--danger); background: rgba(255, 71, 87, 0.1); }\n" +
                "        button.danger:hover { background: var(--danger); color: white; }\n" +
                "        .map-container {\n" +
                "            position: relative; background: #000; border: 1px solid rgba(0, 229, 255, 0.3);\n" +
                "            border-radius: 8px; overflow: hidden; display: flex;\n" +
                "            justify-content: center; align-items: center;\n" +
                "        }\n" +
                "        canvas { display: block; }\n" +
                "        .legend {\n" +
                "            position: absolute; top: 10px; right: 10px; background: rgba(0, 0, 0, 0.7);\n" +
                "            padding: 10px; border-radius: 6px; font-size: 0.8em; border: 1px solid rgba(255,255,255,0.1);\n" +
                "        }\n" +
                "        .legend-item { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }\n" +
                "        .legend-color { width: 12px; height: 12px; border-radius: 2px; }\n" +
                "        .controls-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"sidebar\">\n" +
                "        <div class=\"panel\" style=\"margin-bottom: 20px;\">\n" +
                "            <h1>TeoeWDL</h1>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">System Status</span>\n" +
                "                <span class=\"stat-value\" id=\"recordingStatus\">LOADING</span>\n" +
                "            </div>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">Saved Chunks</span>\n" +
                "                <span class=\"stat-value\" id=\"savedChunks\">0</span>\n" +
                "            </div>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">Saved Chests</span>\n" +
                "                <span class=\"stat-value\" id=\"savedChests\">0</span>\n" +
                "            </div>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">World Seed</span>\n" +
                "                <span class=\"stat-value\" id=\"currentSeed\" style=\"font-size: 0.9em;\">0</span>\n" +
                "            </div>\n" +
                "            <div style=\"display: flex; gap: 10px;\">\n" +
                "                <input type=\"number\" id=\"seedInput\" placeholder=\"New Seed\">\n" +
                "                <button onclick=\"updateSeed()\" style=\"width: auto;\">SET</button>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"panel\">\n" +
                "            <h2>Auto Scanner</h2>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">Scanner Status</span>\n" +
                "                <span class=\"stat-value\" id=\"autoStatus\">IDLE</span>\n" +
                "            </div>\n" +
                "            <div class=\"stat-box\">\n" +
                "                <span class=\"stat-label\">Remaining (Phase)</span>\n" +
                "                <span class=\"stat-value\" id=\"remainingChunks\">0</span>\n" +
                "            </div>\n" +
                "            <div style=\"margin-top: 10px;\">\n" +
                "                <label class=\"stat-label\">Scan Diameter (Blocks)</label>\n" +
                "                <input type=\"number\" id=\"diameter\" value=\"\" min=\"100\" onchange=\"updateConfig()\" style=\"margin-top: 5px; margin-bottom: 10px;\">\n" +
                "                <label class=\"stat-label\">Evasion Bot Prefix (Ignore bots when returning):</label>\n" +
                "                <input type=\"text\" id=\"botPrefix\" placeholder=\"Optional prefix\" onchange=\"updateConfig()\" style=\"margin-top: 5px; margin-bottom: 10px;\">\n" +
                "                <label class=\"stat-label\">Fake Player Prefix (Save to playerdata):</label>\n" +
                "                <input type=\"text\" id=\"fakePlayerPrefix\" placeholder=\"e.g. bot_\" onchange=\"updateConfig()\" style=\"margin-top: 5px; margin-bottom: 10px;\">\n" +
                "                <div style=\"display: flex; align-items: center; margin-bottom: 15px;\">\n" +
                "                    <input type=\"checkbox\" id=\"scanNether\" onchange=\"updateConfig()\" style=\"margin-right: 8px; width: 16px; height: 16px;\">\n" +
                "                    <label class=\"stat-label\" style=\"margin: 0;\">Scan other dimension via Nether Portal automatically</label>\n" +
                "                </div>\n" +
                "                <div style=\"display: flex; align-items: center; margin-bottom: 15px;\">\n" +
                "                    <input type=\"checkbox\" id=\"saveChests\" onchange=\"updateConfig()\" style=\"margin-right: 8px; width: 16px; height: 16px;\">\n" +
                "                    <label class=\"stat-label\" style=\"margin: 0;\">Enable Chests & Containers Extraction</label>\n" +
                "                </div>\n" +
                "                <div class=\"controls-grid\">\n" +
                "                    <button onclick=\"startAuto()\">START SCAN</button>\n" +
                "                    <button class=\"danger\" onclick=\"stopAuto()\">EMERGENCY STOP</button>\n" +
                "                </div>\n" +
                "                <div style=\"margin-top: 10px;\">\n" +
                "                    <button style=\"border-color: var(--success); color: var(--success); background: rgba(46, 213, 115, 0.1);\" onclick=\"window.location.href='/api/download'\">⬇️ DOWNLOAD SAVED WORLD (ZIP)</button>\n" +
                "                </div>\n" +
                "                <div style=\"margin-top: 10px;\">\n" +
                "                    <button style=\"border-color: #ffa502; color: #ffa502; background: rgba(255, 165, 2, 0.1);\" onclick=\"window.open('/api/map.jpg', '_blank')\">🗺️ VIEW TOPOGRAPHICAL MAP (JPG)</button>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"panel\">\n" +
                "            <h2>Console Logs</h2>\n" +
                "            <div id=\"consoleLogs\" style=\"height: 200px; overflow-y: scroll; background: #1e1e1e; color: #a5d6ff; padding: 10px; font-family: monospace; font-size: 12px; border-radius: 4px; border: 1px solid #333; display: flex; flex-direction: column; gap: 4px;\"></div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <div class=\"panel map-container\" id=\"mapContainer\">\n" +
                "        <div class=\"legend\">\n" +
                "            <div class=\"legend-item\"><div class=\"legend-color\" style=\"background: rgba(0, 229, 255, 0.15); border: 1px solid #00e5ff;\"></div><span>Saved Chunk</span></div>\n" +
                "            <div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #ffa502;\"></div><span>Chest/Container</span></div>\n" +
                "            <div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #ff4757; border-radius: 50%;\"></div><span>Player Pos</span></div>\n" +
                "        </div>\n" +
                "        <canvas id=\"mapCanvas\"></canvas>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        let playerPos = [0, 0];\n" +
                "        function updateStatus() {\n" +
                "            fetch('/api/status').then(res => res.json()).then(data => {\n" +
                "                if (document.getElementById('diameter').value === '') {\n" +
                "                    document.getElementById('diameter').value = data.config.diameter;\n" +
                "                    document.getElementById('botPrefix').value = data.config.botPrefix;\n" +
                "                    document.getElementById('fakePlayerPrefix').value = data.config.fakePlayerPrefix;\n" +
                "                    document.getElementById('scanNether').checked = data.config.scanNether;\n" +
                "                    document.getElementById('saveChests').checked = data.config.saveChests;\n" +
                "                }\n" +
                "                const recStatus = document.getElementById('recordingStatus');\n" +
                "                recStatus.innerText = data.isRecording ? 'RECORDING' : 'IDLE';\n" +
                "                recStatus.className = 'stat-value ' + (data.isRecording ? 'active' : 'inactive');\n" +
                "                document.getElementById('currentSeed').innerText = data.currentSeed;\n" +
                "                document.getElementById('savedChunks').innerText = data.savedChunks;\n" +
                "                document.getElementById('savedChests').innerText = data.savedChests;\n" +
                "                const autoStatus = document.getElementById('autoStatus');\n" +
                "                autoStatus.innerText = data.autoActive ? 'SCANNING' : 'IDLE';\n" +
                "                autoStatus.className = 'stat-value ' + (data.autoActive ? 'active' : 'inactive');\n" +
                "                document.getElementById('remainingChunks').innerText = data.remainingChunks + ' (' + data.autoPhase + ')';\n" +
                "            });\n" +
                "            fetch('/api/logs').then(res => res.json()).then(data => {\n" +
                "                const logsDiv = document.getElementById('consoleLogs');\n" +
                "                logsDiv.innerHTML = '';\n" +
                "                data.forEach(msg => {\n" +
                "                    const el = document.createElement('div');\n" +
                "                    el.innerText = msg;\n" +
                "                    logsDiv.appendChild(el);\n" +
                "                });\n" +
                "                logsDiv.scrollTop = logsDiv.scrollHeight;\n" +
                "            });\n" +
                "        }\n" +
                "        function updateMap() {\n" +
                "            fetch('/api/map').then(res => res.json()).then(data => {\n" +
                "                const canvas = document.getElementById('mapCanvas');\n" +
                "                const container = document.getElementById('mapContainer');\n" +
                "                canvas.width = container.clientWidth - 40;\n" +
                "                canvas.height = container.clientHeight - 40;\n" +
                "                const ctx = canvas.getContext('2d');\n" +
                "                ctx.imageSmoothingEnabled = false;\n" +
                "                ctx.clearRect(0, 0, canvas.width, canvas.height);\n" +
                "                playerPos = data.player || [0, 0];\n" +
                "                const chunks = data.chunks || [];\n" +
                "                const chests = data.chests || [];\n" +
                "                ctx.strokeStyle = 'rgba(255,255,255,0.05)';\n" +
                "                ctx.lineWidth = 1;\n" +
                "                for(let i=0; i<canvas.width; i+=50) { ctx.beginPath(); ctx.moveTo(i, 0); ctx.lineTo(i, canvas.height); ctx.stroke(); }\n" +
                "                for(let i=0; i<canvas.height; i+=50) { ctx.beginPath(); ctx.moveTo(0, i); ctx.lineTo(canvas.width, i); ctx.stroke(); }\n" +
                "                if (chunks.length === 0 && chests.length === 0) {\n" +
                "                    ctx.fillStyle = '#94a3b8'; ctx.font = '16px Consolas'; ctx.textAlign = 'center';\n" +
                "                    ctx.fillText('NO MAP DATA AVAILABLE', canvas.width/2, canvas.height/2);\n" +
                "                    return;\n" +
                "                }\n" +
                "                let minX = Infinity, minZ = Infinity, maxX = -Infinity, maxZ = -Infinity;\n" +
                "                for(let c of chunks) {\n" +
                "                    let cx = c[0] * 16, cz = c[1] * 16;\n" +
                "                    if(cx < minX) minX = cx; if(cz < minZ) minZ = cz;\n" +
                "                    if(cx + 16 > maxX) maxX = cx + 16; if(cz + 16 > maxZ) maxZ = cz + 16;\n" +
                "                }\n" +
                "                if (playerPos[0] < minX) minX = playerPos[0]; if (playerPos[0] > maxX) maxX = playerPos[0];\n" +
                "                if (playerPos[1] < minZ) minZ = playerPos[1]; if (playerPos[1] > maxZ) maxZ = playerPos[1];\n" +
                "                const padding = 100;\n" +
                "                minX -= padding; minZ -= padding; maxX += padding; maxZ += padding;\n" +
                "                const w = maxX - minX; const h = maxZ - minZ;\n" +
                "                const scale = Math.min(canvas.width / w, canvas.height / h);\n" +
                "                const offsetX = (canvas.width - w * scale) / 2;\n" +
                "                const offsetY = (canvas.height - h * scale) / 2;\n" +
                "                const toScreen = (x, z) => ({ x: offsetX + (x - minX) * scale, y: offsetY + (z - minZ) * scale });\n" +
                "                if (data.mapBounds && data.mapBounds[0] !== 2147483647) {\n" +
                "                    const img = new Image();\n" +
                "                    img.src = '/api/map.jpg?' + new Date().getTime();\n" +
                "                    img.onload = () => {\n" +
                "                        const imgMinX = data.mapBounds[0] * 16;\n" +
                "                        const imgMinZ = data.mapBounds[1] * 16;\n" +
                "                        const imgMaxX = (data.mapBounds[2] + 1) * 16;\n" +
                "                        const imgMaxZ = (data.mapBounds[3] + 1) * 16;\n" +
                "                        const p1 = toScreen(imgMinX, imgMinZ);\n" +
                "                        const p2 = toScreen(imgMaxX, imgMaxZ);\n" +
                "                        ctx.globalAlpha = 0.8;\n" +
                "                        ctx.drawImage(img, p1.x, p1.y, p2.x - p1.x, p2.y - p1.y);\n" +
                "                        ctx.globalAlpha = 1.0;\n" +
                "                        drawOverlays(ctx, chunks, chests, playerPos, toScreen, scale);\n" +
                "                    };\n" +
                "                } else {\n" +
                "                    drawOverlays(ctx, chunks, chests, playerPos, toScreen, scale);\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "        function drawOverlays(ctx, chunks, chests, playerPos, toScreen, scale) {\n" +
                "                ctx.fillStyle = 'rgba(0, 229, 255, 0.15)';\n" +
                "                ctx.strokeStyle = 'rgba(0, 229, 255, 0.4)';\n" +
                "                ctx.lineWidth = 1;\n" +
                "                for(let c of chunks) {\n" +
                "                    const pos = toScreen(c[0] * 16, c[1] * 16);\n" +
                "                    const size = 16 * scale;\n" +
                "                    ctx.fillRect(pos.x, pos.y, size, size);\n" +
                "                    ctx.strokeRect(pos.x, pos.y, size, size);\n" +
                "                }\n" +
                "                ctx.fillStyle = '#ffa502';\n" +
                "                for(let c of chests) {\n" +
                "                    const pos = toScreen(c[0], c[1]);\n" +
                "                    ctx.beginPath(); ctx.arc(pos.x, pos.y, Math.max(2, 3*scale), 0, Math.PI*2); ctx.fill();\n" +
                "                }\n" +
                "                const pScreen = toScreen(playerPos[0], playerPos[1]);\n" +
                "                ctx.fillStyle = '#ff4757';\n" +
                "                ctx.beginPath();\n" +
                "                ctx.arc(pScreen.x, pScreen.y, Math.max(3, 4 * scale), 0, Math.PI * 2);\n" +
                "                ctx.fill();\n" +
                "                ctx.lineWidth = 2;\n" +
                "                ctx.strokeStyle = 'rgba(255, 71, 87, 0.5)';\n" +
                "                ctx.beginPath(); ctx.arc(pScreen.x, pScreen.y, 15 + Math.sin(Date.now()/200)*5, 0, Math.PI*2); ctx.stroke();\n" +
                "        }\n" +
                "        function updateSeed() {\n" +
                "            const seed = document.getElementById('seedInput').value;\n" +
                "            fetch('/api/seed?value=' + seed, {method: 'POST'}).then(() => {\n" +
                "                document.getElementById('seedInput').value = ''; updateStatus();\n" +
                "            });\n" +
                "        }\n" +
                "        function updateConfig() {\n" +
                "            const diameter = document.getElementById('diameter').value;\n" +
                "            const botPrefix = document.getElementById('botPrefix').value;\n" +
                "            const fakePlayerPrefix = document.getElementById('fakePlayerPrefix').value;\n" +
                "            const scanNether = document.getElementById('scanNether').checked;\n" +
                "            const saveChests = document.getElementById('saveChests').checked;\n" +
                "            if (botPrefix) fetch('/api/auto/botprefix?prefix=' + botPrefix, {method: 'POST'});\n" +
                "            if (fakePlayerPrefix) fetch('/api/auto/fakeprefix?prefix=' + fakePlayerPrefix, {method: 'POST'});\n" +
                "            fetch('/api/config/update?diameter=' + diameter + '&nether=' + scanNether + '&chests=' + saveChests, {method: 'POST'});\n" +
                "        }\n" +
                "        function startAuto() {\n" +
                "            const diameter = document.getElementById('diameter').value;\n" +
                "            const botPrefix = document.getElementById('botPrefix').value;\n" +
                "            const fakePlayerPrefix = document.getElementById('fakePlayerPrefix').value;\n" +
                "            const scanNether = document.getElementById('scanNether').checked;\n" +
                "            const saveChests = document.getElementById('saveChests').checked;\n" +
                "            if (botPrefix) fetch('/api/auto/botprefix?prefix=' + botPrefix, {method: 'POST'});\n" +
                "            if (fakePlayerPrefix) fetch('/api/auto/fakeprefix?prefix=' + fakePlayerPrefix, {method: 'POST'});\n" +
                "            fetch('/api/auto/start?diameter=' + diameter + '&nether=' + scanNether + '&chests=' + saveChests, {method: 'POST'}).then(() => updateStatus());\n" +
                "        }\n" +
                "        function stopAuto() {\n" +
                "            fetch('/api/auto/stop', {method: 'POST'}).then(() => updateStatus());\n" +
                "        }\n" +
                "        setInterval(updateStatus, 1000);\n" +
                "        setInterval(updateMap, 1000);\n" +
                "        window.addEventListener('resize', updateMap);\n" +
                "        updateStatus(); updateMap();\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
        byte[] response = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void serveStatus(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String json = String.format("{\"isRecording\":%b, \"currentSeed\":%d, \"savedChunks\":%d, \"savedChests\":%d, \"autoActive\":%b, \"autoPhase\":\"%s\", \"remainingChunks\":%d, \"config\":{\"diameter\":%d, \"scanNether\":%b, \"saveChests\":%b, \"botPrefix\":\"%s\", \"fakePlayerPrefix\":\"%s\"}}",
                DownloadManager.isRecording(),
                DownloadManager.currentSeed,
                DownloadManager.historicallySavedChunks.size(),
                DownloadManager.savedBlockEntitiesCache.size(),
                AutoScanner.isActive(),
                AutoScanner.getPhaseName(),
                AutoScanner.getRemainingChunks(),
                com.teoe.wdl.ConfigManager.diameter,
                com.teoe.wdl.ConfigManager.scanNether,
                com.teoe.wdl.ConfigManager.saveChests,
                com.teoe.wdl.ConfigManager.botPrefix,
                com.teoe.wdl.ConfigManager.fakePlayerPrefix);
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void serveLogs(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        StringBuilder sb = new StringBuilder("[");
        java.util.List<String> logs = com.teoe.wdl.ModLogger.getLogs();
        boolean first = true;
        for (String log : logs) {
            if (!first) sb.append(",");
            sb.append("\"").append(log.replace("\"", "\\\"").replace("\n", "\\n")).append("\"");
            first = false;
        }
        sb.append("]");
        
        byte[] response = sb.toString().getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        java.io.OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static void serveMapJpg(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        byte[] imageBytes = MapGenerator.generateMapJpg();
        if (imageBytes.length == 0) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"wdl_map.jpg\"");
        exchange.sendResponseHeaders(200, imageBytes.length);
        java.io.OutputStream os = exchange.getResponseBody();
        os.write(imageBytes);
        os.close();
    }

    private static void serveMap(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        double px = 0, pz = 0;
        if (client.player != null) {
            px = client.player.getX();
            pz = client.player.getZ();
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"player\":[").append(px).append(",").append(pz).append("],");
        
        sb.append("\"chunks\":[");
        boolean first = true;
        for (net.minecraft.util.math.ChunkPos pos : DownloadManager.historicallySavedChunks) {
            if (!first) sb.append(",");
            sb.append("[").append(pos.x).append(",").append(pos.z).append("]");
            first = false;
        }
        sb.append("],");

        sb.append("\"chests\":[");
        first = true;
        for (net.minecraft.util.math.BlockPos pos : DownloadManager.savedBlockEntitiesCache.keySet()) {
            if (!first) sb.append(",");
            sb.append("[").append(pos.getX()).append(",").append(pos.getZ()).append("]");
            first = false;
        }
        sb.append("],");
        
        sb.append("\"mapBounds\":[")
          .append(MapGenerator.getMinChunkX()).append(",")
          .append(MapGenerator.getMinChunkZ()).append(",")
          .append(MapGenerator.getMaxChunkX()).append(",")
          .append(MapGenerator.getMaxChunkZ()).append("]");

        sb.append("}");

        byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void handleSeed(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.startsWith("value=")) {
            try {
                DownloadManager.currentSeed = Long.parseLong(query.substring(6));
            } catch (NumberFormatException e) {
            }
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }

    private static void handleConfigUpdate(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("diameter=")) {
                        com.teoe.wdl.ConfigManager.diameter = Integer.parseInt(param.substring(9));
                    }
                    if (param.startsWith("nether=")) {
                        com.teoe.wdl.ConfigManager.scanNether = Boolean.parseBoolean(param.substring(7));
                    }
                    if (param.startsWith("chests=")) {
                        com.teoe.wdl.ConfigManager.saveChests = Boolean.parseBoolean(param.substring(7));
                        com.teoe.wdl.tracker.AutoScanner.saveChests = com.teoe.wdl.ConfigManager.saveChests;
                    }
                }
                com.teoe.wdl.ConfigManager.save();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        }
    }

    private static void handleAutoStart(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            int diameter = 5000;
            boolean nether = false;
            boolean chests = true;
            
            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("diameter=")) {
                        diameter = Integer.parseInt(param.substring(9));
                        com.teoe.wdl.ConfigManager.diameter = diameter;
                    }
                    if (param.startsWith("nether=")) {
                        nether = Boolean.parseBoolean(param.substring(7));
                        com.teoe.wdl.ConfigManager.scanNether = nether;
                    }
                    if (param.startsWith("chests=")) {
                        chests = Boolean.parseBoolean(param.substring(7));
                        com.teoe.wdl.ConfigManager.saveChests = chests;
                    }
                }
                com.teoe.wdl.ConfigManager.save();
            }
            final int d = diameter;
            final boolean n = nether;
            final boolean c = chests;
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                com.teoe.wdl.tracker.AutoScanner.scanNether = n;
                com.teoe.wdl.tracker.AutoScanner.saveChests = c;
                if (!DownloadManager.isRecording()) {
                    DownloadManager.startRecording();
                }
                AutoScanner.startScanning(d);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }

    private static void handleBotPrefix(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("prefix=")) {
                String prefix = query.substring(7);
                com.teoe.wdl.tracker.AutoScanner.ignoredPlayerPrefix = prefix;
                com.teoe.wdl.ConfigManager.botPrefix = prefix;
                com.teoe.wdl.ConfigManager.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }

    private static void handleFakePrefix(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.startsWith("prefix=")) {
                String prefix = query.substring(7);
                com.teoe.wdl.ConfigManager.fakePlayerPrefix = prefix;
                com.teoe.wdl.ConfigManager.save();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }

    private static void handleAutoStop(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
            AutoScanner.stopScanning();
            DownloadManager.stopRecording(); // Automatically save the world when stopping
        });
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().close();
    }

    private static void handleDownloadZip(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            // Wait for DownloadManager to finish saving all pending chunks and level.dat
            while (com.teoe.wdl.DownloadManager.isSaving()) {
                Thread.sleep(200);
            }
            // Give it an extra second to ensure file handles are closed
            Thread.sleep(1000);

            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            String serverName = "DownloadedServer";
            if (client.getCurrentServerEntry() != null) {
                serverName = client.getCurrentServerEntry().address.replaceAll("[^a-zA-Z0-9.-]", "_");
            }
            
            java.io.File saveDir = new java.io.File(client.runDirectory, "wdl_saves/" + serverName + "_WDL");
            
            if (!saveDir.exists()) {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + serverName + "_WDL.zip\"");
            exchange.sendResponseHeaders(200, 0);

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(exchange.getResponseBody())) {
                java.nio.file.Path sourceDir = saveDir.toPath();
                java.nio.file.Files.walk(sourceDir).filter(path -> !java.nio.file.Files.isDirectory(path)).forEach(path -> {
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(sourceDir.relativize(path).toString());
                    try {
                        zos.putNextEntry(zipEntry);
                        java.nio.file.Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            exchange.sendResponseHeaders(500, 0);
            exchange.getResponseBody().close();
        }
    }
}
