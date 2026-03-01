package cn.ts.agent.mcp;

import cn.ts.agent.mcp.config.McpManagerConfig;
import cn.ts.agent.mcp.event.McpConnectionEvent;
import cn.ts.agent.mcp.model.McpConnection;
import cn.ts.agent.mcp.model.McpConnectionConfig;
import cn.ts.agent.mcp.model.McpConnectionStatus;
import cn.ts.agent.mcp.model.McpConnectionType;
import cn.ts.agent.mcp.model.McpStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * MCP 缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌ら幖浣规锭闁搞劍妫冮幃妤呮濞戞瑥鏆堝銈庡亝濞茬喖寮婚敓鐘茬倞闁靛鍎虫禒鑲╃磽娴ｅ搫校闁绘娲熼崺鐐哄箣閿旇棄浜归梺鍛婄懃椤︿即骞冨▎鎾粹拻闁稿本鑹炬禍鐐烘煕閵娿儳鍩ｇ€殿喖顭峰鎾偄閾忚鍟庨梻浣稿閻撳牓宕伴弽銊﹀弿闁靛繈鍊栭埛鎴︽煕濠靛棗顏存俊鏌ヮ棑缁辨帡顢欓悾灞惧櫚閻庢鍠栭…鐑藉极閹版澘妞藉ù锝呮惈瀵娊姊绘担鍛婂暈婵炶绠撳畷鎴﹀箳閹宠櫕鐩畷鍗炩槈濞嗘垵骞? * <p>
 * 闂傚倸鍊搁崐宄懊归崶褏鏆﹂柛顭戝亝閸欏繘鏌ｉ姀銏╃劸缂佲偓婢跺绻嗛柕鍫濇噺閸ｅ湱绱掗幇顓ф疁闁哄矉绻濆畷鍫曞Ψ閵壯傜棯闂?MCP 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曚綅閸ヮ剦鏁冮柨鏇楀亾闁汇倗鍋撶换婵嬫濞戝崬鍓扮紒鐐劤椤兘寮婚敐澶婄疀妞ゆ帒鍊风划鐢告⒑閸濆嫭顥炵紒顔芥崌瀵鎮㈤搹鍦紲闂侀潧绻掓慨鐢告倶瀹ュ鈷戠紒瀣健椤庢绱掓径濠勭Ш鐎殿喛顕ч埥澶愬閻樻彃绁梻渚€娼ч…鍫ュ磿鏉堚晝涓嶆繛宸簼閳锋垿鏌涘┑鍡楊仾闁革絾妞介弻鐔碱敊閻撳簶鍋撻崹顕呭殨閻犲洦绁村Σ鍫熶繆椤栫偞鏁辨い顐㈢Т閳规垿鎮╃拠褍浼愰柣搴㈣壘閸㈣尪鐏嬮梺鍛婃处閸ㄩ亶鍩涢幋锔界厽闁绘柨鎲＄欢鏌ユ煛閸℃岸鍝哄ǎ鍥э躬椤㈡洟鏁愰崶鈺冩殼婵＄偑鍊栧ú鈺冪礊娓氣偓楠炲啯绂掔€ｎ亜绐涙繝鐢靛Т閸婇鑺辩拠娴嬫斀闁绘劕鐡ㄧ亸浼存煕閹存繄绉虹€规洝顫夌€靛ジ寮堕幋鐘垫毇濠电偞鎸婚崺鍐磻閹惧瓨鍙忓┑鐘插暞閵囨繈鏌熺粵鍦瘈濠碘€崇埣瀹曘劑顢楅崒娑樼婵犵數濮烽。钘壩ｉ崨鏉戠；闁告洦鍨奸弫瀣亜閹捐泛鈧綁鏁愭径濠勭杸濡炪倖鍨兼慨銈夊棘閳ь剟姊虹拠鎻掑毐缂傚秴妫欑粋宥夋倷閺嶇娲弻鍡楊吋閸″繑瀚奸梻浣告啞缁嬫垿鏁冮敐鍥偨闂侇剙绉甸悡鏇㈡煟濡櫣锛嶅褍鐏氶妵鍕閳╁喚妫冨銈冨灪濡啫鐣锋總鍛婂亜闁告繂瀚粻鐐烘⒒閸屾瑧顦︽繝鈧柆宥呯？闁靛牆顦粻鏉课旈敐鍛殭闁绘挴鈧剚鐔嗛悹楦挎閻忚京鐥崣銉х煓闁哄本绋撴禒锕傚礈瑜夋慨鍥倵鐟欏嫭绀€闁兼椿鍨堕崺鐐哄箣閿曗偓閻忓磭鈧娲栧ú锕傚箟婵傚憡鈷戦柣鐔告緲濡插鏌熼搹顐€挎鐐插暣楠炲棜顦撮柡鍡楁閺屽秷顧侀柛鎾跺枛閵嗕礁鈻庨幘鍐插祮闂侀潧绻嗗褔骞忓ú顏呯厽闁绘柨鎽滈幊鍐倵濮橀棿閭柟铏矒椤㈡棃宕奸悢鍝勫箞闂佺懓鍚嬮幆宀勫窗閺嶎厽鍊堕柨鏇炲€归悡鏇㈡煃鐟欏嫬鍔ゅù婊呭亾娣囧﹪鎮欓鍕ㄥ亾閺嶎厼鍨傞柣鎾崇岸閺嬫牠鏌￠崶锝嗗殟闁搞儺鍓氶弲婵嬫煃瑜滈崜鐔凤耿娓氣偓濮婅櫣绱掑鍡欏姼闁诲繐绻戦悷鈺呯嵁濡ゅ懏鍋愮紓浣诡焽閸橀潧顪冮妶鍡欏缂佸甯¤棢闁割偁鍎查悡娑㈡倶閻愰鍤欏┑鈥虫健閺屽秷顧侀柛鎾卞妿瀵板﹥绂掔€ｎ偄鈧埖鎱ㄥΟ鎸庣【闁绘挻娲熼弻锟犲炊閵夈儳浠鹃梺鎶芥敱閸ㄥ潡寮诲☉妯锋斀闁糕剝顨忔导鍌氼渻閵堝骸浜剧紒鐘虫崌瀵鈽夊▎鎰妳闂侀潧顭堥崐鏍窗濮橆儵鏃堟偐闂堟稐绮剁紓浣虹帛閸ㄥ綊鍩€椤戣法绁烽柛瀣姍閸┾偓妞ゆ帊鑳堕埊鏇㈡煥濮橆厾绠鹃柛顐ゅ枔閻﹥銇勯鍕殻濠碘€崇埣瀹曘劑顢涘搴℃暪闂備浇顕х€涒晠宕欒ぐ鎺戠婵犻潧鐟掗悜钘夌妞ゆ牗绋堥幏濠氭⒑缁嬫寧婀伴柣鐔濆洤绀夌€广儱娲﹂崰鎰版煛婢跺﹦浠㈤柡鍡欏枔缁辨帡顢欓懞銉㈡嫻濡炪倧濡囨晶妤呭箚閺冨牆顫呴柣妯垮皺閸濆骸鈹戦悩鍨毄闁稿鍋ゅ畷褰掝敍閻愭彃鐎紒缁㈠幖閹虫劗绮? * </p>
 *
 * @author tianshuo
 */
public class McpManagerImpl implements McpManager {

    private static final Logger logger = LoggerFactory.getLogger(McpManagerImpl.class);
    private static final String DEFAULT_SSE_ENDPOINT = "/sse";

    private final McpManagerConfig config;
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();
    private volatile ScheduledExecutorService scheduler;
    private volatile ExecutorService connectionExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ApplicationEventPublisher eventPublisher;
    private ScheduledFuture<?> healthCheckTask;

    record SseTarget(String baseUrl, String sseEndpoint) {
    }

    public McpManagerImpl(McpManagerConfig config, ApplicationEventPublisher eventPublisher) {
        this.config = config;
        this.eventPublisher = eventPublisher;
        ensureExecutorsReady();
    }

    private ScheduledExecutorService createScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "mcp-manager-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    private ExecutorService createConnectionExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "mcp-manager-connector");
            thread.setDaemon(true);
            return thread;
        });
    }

    private synchronized void ensureExecutorsReady() {
        boolean schedulerUnavailable = scheduler == null || scheduler.isShutdown() || scheduler.isTerminated();
        boolean connectionExecutorUnavailable = connectionExecutor == null
                || connectionExecutor.isShutdown()
                || connectionExecutor.isTerminated();
        if (!schedulerUnavailable && !connectionExecutorUnavailable) {
            return;
        }

        shutdownExecutors();
        scheduler = createScheduler();
        connectionExecutor = createConnectionExecutor();
    }

    private synchronized void shutdownExecutors() {
        ScheduledExecutorService schedulerRef = this.scheduler;
        ExecutorService connectionExecutorRef = this.connectionExecutor;

        this.scheduler = null;
        this.connectionExecutor = null;

        if (schedulerRef != null) {
            schedulerRef.shutdown();
            try {
                if (!schedulerRef.awaitTermination(5, TimeUnit.SECONDS)) {
                    schedulerRef.shutdownNow();
                }
            } catch (InterruptedException e) {
                schedulerRef.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (connectionExecutorRef != null) {
            connectionExecutorRef.shutdownNow();
        }
    }

    /**
     * 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫鎾绘偐閸愯弓鐢婚梻浣瑰濞叉牠宕愰幖浣稿瀭闁稿瞼鍋為悡鐔兼煏韫囧鐏柡鍡忔櫊閺?MCP 闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曚綅閸ヮ剦鏁冮柨鏇楀亾闁汇倗鍋撶换婵嬫濞戝崬鍓扮紒鐐劤椤兘寮婚敐澶婄疀妞ゆ帒鍊风划鐢告⒑閸濆嫭顥炵紒顔芥崌楠炲啫螖閳ь剟鍩ユ径濞炬瀻婵☆垳鍘ф慨鐑樹繆閻愵亜鈧劙寮插鍫熷亗闁跨喓濮寸粻鏍ㄤ繆閵堝懏鍣洪柡鍛叀閺屾稓浠﹂崜褉濮囬梺缁樼箖濞叉牠鈥?
     */
    private void publishEvent(McpConnectionEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish MCP connection event: {}", event, e);
        }
    }

    @Override
    public boolean registerConnection(McpConnectionConfig config) {
        if (!StringUtils.hasText(config.getName())) {
            throw new IllegalArgumentException("Connection name must not be blank");
        }
        if (connections.containsKey(config.getName())) {
            throw new IllegalArgumentException("Connection name already exists: " + config.getName());
        }

        try {
            McpConnection connection = new McpConnection();
            connection.setName(config.getName());
            connection.setConfig(config);
            connection.setStatus(McpConnectionStatus.CONNECTING);

            connections.put(config.getName(), connection);
            connectAsync(connection);

            logger.info("MCP 闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻弻鈩冨緞鐎ｎ亞浜堕梺鍝勵儐閻楃娀寮婚弴鐔虹闁割煈鍠栨慨搴ㄦ⒑閸濆嫬鈧敻宕戦幘缁樷拻濞达絿鍎ら崵鈧梺瀹︽澘濮傜€规洘绻堝鎾偄缁嬪灝浼? {}, 缂傚倸鍊搁崐椋庢閿熺姴纾诲鑸靛姦閺佸鎲搁弮鍫濈畺? {}", config.getName(), config.getType());
            return true;
        } catch (Exception e) {
            logger.error("MCP 闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻弻鈩冨緞鐎ｎ亞浜堕梺鍝勵儐閻楃娀寮婚弴鐔虹闁割煈鍠栨慨搴ㄦ⒑閸濆嫬鈧敻宕戦幘鏂ユ斀闁绘﹩鍠栭悘顏堟煕鎼淬倕浜归柛鎺撳浮瀹曟粏顦抽柛? {}", config.getName(), e);
            connections.remove(config.getName());
            return false;
        }
    }

    @Override
    public void unregisterConnection(String name) {
        McpConnection connection = connections.remove(name);
        if (connection == null) {
            throw new IllegalArgumentException("闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻幃妤€鈽夊▎妯煎姺闂佸磭绮濠氬焵椤掆偓缁犲秹宕曢柆宥呯疇闁圭増婢樼粻娲煟濡偐甯涢柣? " + name);
        }

        disconnect(connection);
        logger.info("MCP 闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻弻鈩冨緞鐎ｎ亞鍔搁梺鍝ュ枎閹虫﹢寮诲☉妯锋瀻闊浄绲炬晥闂備礁鎼Λ妤呮偋閹炬剚娼栭柣鎴炆戦崕鐔兼煙閹冨笭濠? {}", name);
    }

    @Override
    public void updateConnection(String name, McpConnectionConfig newConfig) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            throw new IllegalArgumentException("闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻幃妤€鈽夊▎妯煎姺闂佸磭绮濠氬焵椤掆偓缁犲秹宕曢柆宥呯疇闁圭増婢樼粻娲煟濡偐甯涢柣? " + name);
        }

        disconnect(connection);
        connection.setConfig(newConfig);
        connection.setStatus(McpConnectionStatus.CONNECTING);
        connection.setErrorMessage(null);
        connectAsync(connection);

        logger.info("MCP 闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻弻鈩冨緞鐎ｎ亞鍔搁梺鍝ュ枎閹虫﹢寮诲☉妯锋瀻闊浄绲炬晥缂傚倷鑳堕搹搴ㄥ垂閸洖钃? {}", name);
    }

    @Override
    public Optional<McpConnection> getConnection(String name) {
        return Optional.ofNullable(connections.get(name));
    }

    @Override
    public List<McpConnection> getAllConnections() {
        return new ArrayList<>(connections.values());
    }

    @Override
    public List<McpSyncClient> getAllMcpClients() {
        return connections.values().stream()
                .filter(c -> c.getStatus() == McpConnectionStatus.CONNECTED)
                .map(McpConnection::getClient)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public ToolCallback[] getAllToolCallbacks() {
        return new ToolCallback[0];
    }

    @Override
    public boolean healthCheck(String name) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            logger.warn("闂傚倸鍊烽懗鍫曗€﹂崼銉晞闁告劦鍠栫壕鍧楁⒑椤掆偓缁夊绮婚弽顐熷亾楠炲灝鍔氶柟鍙壝埥澶愬閻橀潧濮︽俊鐐€栫敮鎺斺偓姘煎墴閹锋垿鎮㈤崗鑲╁帾婵犮垼娉涢悧鍡涘礉濡ソ鐟邦煥閸愶箑浠梺璇″枛缂嶅﹪鐛崶顒€鐓涘ù锝囶焾鍟哥紓鍌氬€峰ù鍥ㄣ仈閸濄儲宕查柛鎰电厛閻掕棄鈹戦悩宕囶暡闁稿﹦鍏橀弻鈩冨緞鐎ｎ亞浠村銈忕畱瀵墎鎹㈠☉銏犵闁绘劖娼欑喊宥囩磽娴ｅ壊妲归柟鍛婂▕楠炲啫煤椤忓嫀鈺呮煃鏉炴壆鍔嶇€? {}", name);
            return false;
        }

        return performHealthCheck(connection);
    }

    @Override
    public Map<String, Boolean> healthCheckAll() {
        Map<String, Boolean> results = new HashMap<>();
        for (String name : connections.keySet()) {
            results.put(name, healthCheck(name));
        }
        return results;
    }

    @Override
    public void reconnect(String name) {
        McpConnection connection = connections.get(name);
        if (connection == null) {
            throw new IllegalArgumentException("闂傚倷绀侀幖顐λ囬锕€鐤炬繝闈涱儏绾惧鏌ｉ幇顒備粵闁哄棙绮撻幃妤€鈽夊▎妯煎姺闂佸磭绮濠氬焵椤掆偓缁犲秹宕曢柆宥呯疇闁圭増婢樼粻娲煟濡偐甯涢柣? " + name);
        }

        logger.info("闂傚倸鍊风粈浣虹礊婵犲偆鐒界憸鏃堛€侀弽顓炲窛妞ゆ棁妫勫鍧楁⒑閸愬弶鎯堥柟鍐茬箻閹繝宕掑┃鎯т壕妤犵偛鐏濋崝姘舵煟濡も偓缁绘帞鍒? {}", name);
        disconnect(connection);
        connection.setStatus(McpConnectionStatus.CONNECTING);
        connection.setErrorMessage(null);
        connectAsync(connection);
    }

    @Override
    public McpStatistics getStatistics() {
        McpStatistics.McpStatisticsBuilder builder = McpStatistics.builder();

        int connectedCount = 0;
        int connectingCount = 0;
        int errorCount = 0;
        int disconnectedCount = 0;
        long totalCalls = 0;
        long successfulCalls = 0;
        long failedCalls = 0;
        double totalResponseTime = 0;
        int totalTools = 0;

        Map<String, McpStatistics.ConnectionStatistics> connStats = new HashMap<>();

        for (McpConnection connection : connections.values()) {
            switch (connection.getStatus()) {
                case CONNECTED -> connectedCount++;
                case CONNECTING -> connectingCount++;
                case ERROR -> errorCount++;
                case DISCONNECTED -> disconnectedCount++;
            }

            McpConnection.ConnectionStatistics stats = connection.getStatistics();
            long connTotalCalls = stats.getTotalCalls().get();
            long connSuccessfulCalls = stats.getSuccessfulCalls().get();
            long connFailedCalls = stats.getFailedCalls().get();

            totalCalls += connTotalCalls;
            successfulCalls += connSuccessfulCalls;
            failedCalls += connFailedCalls;

            double avgResponse = stats.getAverageResponseTime();
            totalResponseTime += avgResponse * connSuccessfulCalls;

            // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾鐎规洏鍎抽埀顒婄秵閸犳牜澹曢崸妤佺厵闁诡垳澧楅ˉ澶愬箹閺夋埊韬柡灞诲€濋幊婵嬪箥椤旇偐澧┑鐐茬摠缁瞼绱炴繝鍥ц摕婵炴垯鍨瑰敮闂佺懓鐡ㄧ换鍡欌偓姘嵆閺岋繝宕ㄩ姘ｆ瀰濠殿喖锕ュ钘壩涢崘銊㈡閺夊牄鍓遍妶澶嬧拺缂佸娼￠妤冣偓瑙勬处閸撶喖銆佸璺何ㄩ柍杞拌兌閻嫰姊洪崜鎻掍簽闁哥姵鎹囧畷銏ゅ川婵犲嫮顔曢柡澶婄墕婢т粙宕氭导瀛樼厱闁靛繆鈧磭绁烽梺浼欑悼閺佽鐣烽崡鐐╂婵炲棙鍨甸獮?
            int toolCount = 0;
            if (connection.getClient() != null) {
                try {
                    var tools = connection.getClient().listTools();
                    if (tools != null) {
                        toolCount = tools.tools().size();
                    }
                } catch (Exception e) {
                    logger.debug("闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾鐎规洏鍎抽埀顒婄秵閸犳牜澹曢崸妤佺厵闁诡垳澧楅ˉ澶愬箹閺夋埊韬柡灞诲€濋幊婵嬪箥椤旇偐澧┑鐐茬摠缁瞼绱炴繝鍥ц摕婵炴垯鍨瑰敮闂佺懓鐡ㄧ换鍡欌偓姘嵆閺岋繝宕ㄩ姘ｆ瀰濠殿喖锕ュ钘壩涢崘銊㈡閺夊牄鍓遍妶澶嬧拺缂佸娼￠妤冣偓瑙勬处閸撶喖銆佸璺何ㄩ柍杞拌兌閻嫰姊洪崜鎻掍簽闁哥姵鎹囧畷銏ゅ川婵犲嫮顔曢柡澶婄墕婢т粙宕氭导瀛樼厱闁靛繆鈧磭绁烽梺浼欑悼閺佽鐣烽崡鐐╂婵炲棙鍨甸獮鍫ユ⒒娴ｇ懓顕滅紒璇插€哥叅闁靛牆顦粈澶愭倵閿濆骸鍘撮柛瀣崌瀹曞綊顢曢敐鍥у殥闂佽瀛╅崙褰掑储婵傜硶鈧箓宕堕鍡欐澑濠电偞鍨兼ご鎼佸疾濠靛鈷? {}", connection.getName(), e);
                }
            }
            totalTools += toolCount;

            McpStatistics.ConnectionStatistics cs = McpStatistics.ConnectionStatistics.builder()
                    .name(connection.getName())
                    .status(connection.getStatus())
                    .totalCalls(connTotalCalls)
                    .successfulCalls(connSuccessfulCalls)
                    .failedCalls(connFailedCalls)
                    .averageResponseTime(avgResponse)
                    .toolCount(toolCount)
                    .lastCallTime(stats.getLastCallTime())
                    .build();
            connStats.put(connection.getName(), cs);
        }

        return builder
                .totalConnections(connections.size())
                .connectedCount(connectedCount)
                .connectingCount(connectingCount)
                .errorCount(errorCount)
                .disconnectedCount(disconnectedCount)
                .totalTools(totalTools)
                .totalCalls(totalCalls)
                .successfulCalls(successfulCalls)
                .failedCalls(failedCalls)
                .averageResponseTime(successfulCalls > 0 ? totalResponseTime / successfulCalls : 0)
                .connectionStatistics(connStats)
                .timestamp(Instant.now())
                .build();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("MCP 缂傚倸鍊烽懗鑸靛垔鐎靛憡顫曢柡鍥ュ灩缁犳牕鈹戦悩鍙夋悙闂傚偆鍨堕弻鏇熺箾閸喖濮曢梺绋垮閻熲晠寮诲☉銏犳閻犲洦褰冮‖鍡涙⒑?..");

            ensureExecutorsReady();

            if (config.isEnableHealthCheck()) {
                startHealthCheckTask();
            }

            if (config.isAutoConnectOnStartup()) {
                for (McpConnection connection : connections.values()) {
                    if (connection.getStatus() == McpConnectionStatus.DISCONNECTED
                            || connection.getStatus() == McpConnectionStatus.ERROR) {
                        connection.setStatus(McpConnectionStatus.CONNECTING);
                        connection.setErrorMessage(null);
                        connectAsync(connection);
                    }
                }
            }

            logger.info("MCP manager started");
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("MCP 缂傚倸鍊烽懗鑸靛垔鐎靛憡顫曢柡鍥ュ灩缁犳牕鈹戦悩鍙夋悙闂傚偆鍨堕弻鏇熺箾閸喖濮嶇紓渚囧枤缁垶骞堥妸銉㈡斀闁归偊鍘剧粣鏃堟⒑?..");

            if (healthCheckTask != null) {
                healthCheckTask.cancel(false);
                healthCheckTask = null;
            }

            for (McpConnection connection : connections.values()) {
                disconnect(connection);
            }

            shutdownExecutors();

            logger.info("MCP manager stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void connectAsync(McpConnection connection) {
        ensureExecutorsReady();
        ExecutorService executor = connectionExecutor;
        Duration timeout = resolveConnectionTimeout(connection);
        CompletableFuture.runAsync(() -> connectWithTimeout(connection, timeout), executor)
                .exceptionally(ex -> {
                    handleConnectionFailure(connection, unwrapCompletionException(ex));
                    return null;
                });
    }

    private void connectWithTimeout(McpConnection connection, Duration timeout) {
        ensureExecutorsReady();
        ExecutorService executor = connectionExecutor;
        Future<?> future = executor.submit(() -> connect(connection));
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException("MCP connection timeout after " + timeout.toSeconds() + "s", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("MCP connection interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private Duration resolveConnectionTimeout(McpConnection connection) {
        return resolveConnectionTimeout(connection.getConfig());
    }

    private Duration resolveConnectionTimeout(McpConnectionConfig connectionConfig) {
        Duration timeout = connectionConfig.getTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = config.getDefaultTimeout();
        }
        return (timeout == null || timeout.isNegative() || timeout.isZero()) ? Duration.ofSeconds(30) : timeout;
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if ((throwable instanceof CompletionException || throwable instanceof ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void handleConnectionFailure(McpConnection connection, Throwable throwable) {
        if (!running.get() && connection.getStatus() == McpConnectionStatus.DISCONNECTED) {
            logger.debug("Ignore connection failure for {} because manager is stopped", connection.getName());
            return;
        }

        String errorMessage = throwable == null ? "Unknown MCP connection error" : throwable.getMessage();
        logger.error("Connection failed: {}", connection.getName(), throwable);
        connection.setStatus(McpConnectionStatus.ERROR);
        connection.setErrorMessage(errorMessage);

        publishEvent(McpConnectionEvent.error(this, connection.getName(), errorMessage));

        if (connection.getConfig().isAutoReconnect()) {
            scheduleReconnect(connection);
        }
    }

    private void connect(McpConnection connection) {
        McpConnectionConfig config = connection.getConfig();
        McpSyncClient client = createMcpClient(config);
        client.initialize();
        logger.info("MCP 瀹㈡埛绔垵濮嬪寲鎴愬姛");

        connection.setClient(client);
        connection.setStatus(McpConnectionStatus.CONNECTED);
        connection.setConnectedAt(Instant.now());
        connection.setErrorMessage(null);

        logger.info("MCP 杩炴帴鎴愬姛: {}, 绫诲瀷: {}", config.getName(), config.getType());
        publishEvent(McpConnectionEvent.connected(this, config.getName()));
    }

    private McpSyncClient createMcpClient(McpConnectionConfig config) {
        ObjectMapper objectMapper = new ObjectMapper();
        Duration timeout = resolveConnectionTimeout(config);

        return switch (config.getType()) {
            case STDIO -> {
                if (config.getStdioConfig() == null) {
                    throw new IllegalArgumentException("STDIO configuration must not be null");
                }
                ServerParameters params = ServerParameters
                        .builder(config.getStdioConfig().getCommand())
                        .args(config.getStdioConfig().getArgs())
                        .env(config.getStdioConfig().getEnv())
                        .build();
                McpClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(objectMapper));
                yield buildSyncClient(transport, timeout);
            }

            case SSE -> {
                if (config.getSseConfig() == null) {
                    throw new IllegalArgumentException("SSE configuration must not be null");
                }

                SseTarget target = resolveSseTarget(config.getSseConfig());
                Map<String, String> headers = config.getSseConfig().getHeaders() != null
                        ? config.getSseConfig().getHeaders()
                        : Collections.emptyMap();

                try {
                    Set<String> headerKeys = headers.entrySet().stream()
                            .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    logger.info("Creating SSE client, name={}, baseUrl={}, sseEndpoint={}, timeout={}s, headerKeys={}",
                            config.getName(), target.baseUrl(), target.sseEndpoint(), timeout.toSeconds(), headerKeys);

                    HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport
                            .builder(target.baseUrl())
                            .sseEndpoint(target.sseEndpoint())
                            .connectTimeout(timeout)
                            .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                            .customizeRequest(requestBuilder -> headers.forEach((key, value) -> {
                                if (StringUtils.hasText(key) && value != null) {
                                    requestBuilder.header(key, value);
                                }
                            }));

                    McpClientTransport transport = transportBuilder.build();
                    McpSyncClient client = buildSyncClient(transport, timeout);
                    logger.info("SSE client created successfully");
                    yield client;
                } catch (Exception e) {
                    logger.error("Failed to create SSE client for name={}, baseUrl={}, sseEndpoint={}",
                            config.getName(), target.baseUrl(), target.sseEndpoint(), e);
                    throw new RuntimeException("Failed to create SSE client: " + e.getMessage(), e);
                }
            }

            case HTTP -> {
                logger.warn("HTTP transport is not implemented yet, please use SSE");
                throw new UnsupportedOperationException("HTTP transport is not implemented yet, please use SSE");
            }
        };
    }

    McpSyncClient buildSyncClient(McpClientTransport transport, Duration timeout) {
        return McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
    }

    SseTarget resolveSseTarget(McpConnectionConfig.SseConfig sseConfig) {
        if (sseConfig == null) {
            throw new IllegalArgumentException("SSE config must not be null");
        }

        if (StringUtils.hasText(sseConfig.getBaseUrl())) {
            String baseUrl = trimTrailingSlash(sseConfig.getBaseUrl().trim());
            String endpoint = normalizeSseEndpoint(sseConfig.getSseEndpoint());
            return new SseTarget(baseUrl, endpoint);
        }

        if (!StringUtils.hasText(sseConfig.getUrl())) {
            throw new IllegalArgumentException("SSE URL or baseUrl must not be empty");
        }

        try {
            URI uri = URI.create(sseConfig.getUrl().trim());
            if (!uri.isAbsolute() || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getAuthority())) {
                throw new IllegalArgumentException("SSE URL must be absolute");
            }

            String baseUrl = trimTrailingSlash(new URI(uri.getScheme(), uri.getAuthority(), null, null, null).toString());
            String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : DEFAULT_SSE_ENDPOINT;
            String endpoint = StringUtils.hasText(uri.getQuery()) ? path + "?" + uri.getQuery() : path;
            return new SseTarget(baseUrl, normalizeSseEndpoint(endpoint));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid SSE URL: " + sseConfig.getUrl(), e);
        }
    }

    private String normalizeSseEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return DEFAULT_SSE_ENDPOINT;
        }

        String normalized = endpoint.trim();
        if (normalized.startsWith("?")) {
            return DEFAULT_SSE_ENDPOINT + normalized;
        }
        if (normalized.startsWith("/") || normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "/" + normalized;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
    private void disconnect(McpConnection connection) {
        if (connection.getClient() != null) {
            try {
                connection.getClient().close();
            } catch (Exception e) {
                logger.warn("闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ磵閳ь剨绠撳畷濂稿閳ュ啿绨ラ梻浣烘嚀椤曨參宕戦悢铏逛笉闁诡垎鈧弨浠嬫煟濡灝绱﹂弶?MCP 闂傚倸鍊搁崐宄懊归崶顒夋晪鐟滃酣銆冮妷鈺佷紶闁靛／鍌滅憹闁诲骸绠嶉崕閬嵥囬鐐插瀭闁稿本绋撶粻鍓р偓鐟板閸犳洜鑺辨繝姘厸闁告洍鏅涢崝婊呯磼缂佹娲存鐐差儔閹瑧鈧潧鎲￠濠氭⒒娴ｅ憡鍟炴い銊ユ閸犲﹤顓兼径濞箓鏌涢弴銊ョ仩闁告劏鍋撻梻渚€娼ч…鍫ュ磿閺屻儱绠氭繛鎴炵懅缁犻箖鎮楀☉娆樼劷闁活厼锕弻鏇㈠醇閻旂鈧劖顨? {}", connection.getName(), e);
            }
            connection.setClient(null);
        }
        connection.setStatus(McpConnectionStatus.DISCONNECTED);

        // 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偛顦甸弫鎾绘偐閸愯弓鐢婚梻浣瑰濞叉牠宕愰幖浣稿瀭闁稿瞼鍋為悡鐔兼煏韫囧鐏柡鍡忔櫊閺屾稑螖娴ｇ鎽甸梺鍝勭焿缂嶄線鐛€ｎ喗鏅查柛鈾€鏅滈ˉ澶嬬節绾版ɑ顫婇柛瀣嚀閹筋偊姊洪棃娑欐悙閻庢矮鍗抽悰顔嘉熼崗鐓庣／闁哄鍋熸晶妤呭春濞戞氨纾藉ù锝呮惈娴滈箖鏌涙惔銏犫枙闁炽儻绠戦悾锟犳焽閿曗偓濞堛劑姊洪崜鎻掍簼婵炲弶鐗犻幃鈥斥槈閵忥紕鍘遍梺瑙勫閺呮稒淇婇悜妯肩鐎光偓婵犱線鍋楅梺鍝勭焿缂嶄焦鎱ㄩ埀顒勬煃鏉炴媽鍏屽ù鐘櫊濮婅櫣鈧櫢闄勫妯讳繆鐠恒劎纾兼い鏃傗拡閸庡繑銇勯幘鐐藉仮鐎规洖鐖奸弫鎰板川椤掆偓椤ユ岸姊绘担鐟邦嚋缂佽鍊垮畷鎰版偡閹佃櫕鐎洪梺绯曞墲鑿уù?        publishEvent(McpConnectionEvent.disconnected(this, connection.getName()));
    }

    /**
     * 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗ù锝夋交閼板潡姊洪鈧粔鐢稿磻閿熺姵鐓欓柟顖滃椤ュ顨ラ悙顏勭伈闁哄苯绉瑰畷顐﹀礋椤愮喎浜剧憸鐗堝笒缁狙囨煙闂傚鍔嶉柍閿嬪笒闇夐柨婵嗗椤掔喖鏌ｉ幒鏂夸壕闁靛洤瀚伴獮瀣攽閸℃ɑ顔掗柣搴ゎ潐濞叉ê煤閻旂厧钃熼柛鈩冾殢閸氬鏌涘☉鍗炵仯闁哄棭鍋婇弻锝嗘償閿涘嫮鏆涢梺绋块叄娴滃爼鐛径濞㈢喓鎮伴埄鍐炬綌闂備線娼х换鎺撴叏閻戣棄绀夐柨鏇楀亾妞ゎ亜鍟存俊鍫曞幢濡ゅ啰鎳嗛梺璇插閸戝綊宕滈悢鐓庣?     */
    private boolean performHealthCheck(McpConnection connection) {
        if (connection.getClient() == null) {
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage("MCP client not initialized");
            publishEvent(McpConnectionEvent.error(this, connection.getName(), "MCP client not initialized"));
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            connection.getClient().listTools();
            long responseTime = System.currentTimeMillis() - startTime;

            connection.getStatistics().recordSuccess(responseTime);
            connection.setStatus(McpConnectionStatus.CONNECTED);
            connection.setErrorMessage(null);
            return true;
        } catch (Exception e) {
            logger.warn("Health check failed: {}", connection.getName(), e);
            connection.getStatistics().recordFailure();
            connection.setStatus(McpConnectionStatus.ERROR);
            connection.setErrorMessage(e.getMessage());
            publishEvent(McpConnectionEvent.error(this, connection.getName(), e.getMessage()));

            if (connection.getConfig().isAutoReconnect()) {
                scheduleReconnect(connection);
            }
            return false;
        }
    }

    private void startHealthCheckTask() {
        ensureExecutorsReady();
        Duration interval = config.getHealthCheckInterval();
        ScheduledExecutorService schedulerRef = scheduler;
        healthCheckTask = schedulerRef.scheduleAtFixedRate(() -> {
            try {
                logger.debug("Running health check task...");
                healthCheckAll();
            } catch (Exception e) {
                logger.error("Health check task execution failed", e);
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduleReconnect(McpConnection connection) {
        ensureExecutorsReady();
        Duration retryInterval = connection.getConfig().getRetryInterval();
        ScheduledExecutorService schedulerRef = scheduler;
        schedulerRef.schedule(() -> {
            if (connection.getStatus() != McpConnectionStatus.CONNECTED) {
                logger.info("Trying reconnect: {}", connection.getName());
                disconnect(connection);
                connection.setStatus(McpConnectionStatus.CONNECTING);
                connection.setErrorMessage(null);
                connectAsync(connection);
            }
        }, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
    }
}
