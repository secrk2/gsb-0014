/* ============================================================
 * 腕表离线定位队列（矫正对象端）
 * - 每 5 秒一帧：GPS 坐标 + 设备状态（电量/信号/是否佩戴）
 * - 时间一律取 UTC 瞬间（ISO-8601 带 Z），不发送设备本地墙钟，
 *   服务端按司法所时区判定“今天/禁行时段”，杜绝跨时区误判
 * - 断网期间定位点写入本地队列（localStorage 持久化，杀进程不丢）
 * - 恢复网络后整队列批量补传；服务端按 clientPointId 幂等去重、
 *   按采集时间合并，并做 GPS 漂移质检，重放不产生重复轨迹点
 * ============================================================ */
(function (global) {
  const QUEUE_KEY = 'jwt_track_queue_v2';
  const LOG_KEY = 'jwt_track_log_v2';

  function load(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key) || 'null') || fallback; }
    catch { return fallback; }
  }
  function save(key, val) { localStorage.setItem(key, JSON.stringify(val)); }

  function uuid() {
    if (global.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'pt-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
      + '-' + Math.random().toString(36).slice(2, 6);
  }

  /** UTC 瞬间 ISO（带 Z），匹配后端 Instant；替代旧的“无时区本地时间” */
  function utcIso(d) {
    return new Date(d.getTime()).toISOString();
  }

  const TrackQueue = {
    queue: load(QUEUE_KEY, []),
    logs: load(LOG_KEY, []),
    online: navigator.onLine,
    // 演示用：模拟乡村断网（即使设备有网络也按离线处理）
    simOffline: false,
    syncing: false,
    listeners: [],
    // 腕表设备状态（真实设备由蓝牙/系统 API 提供，演示可手动模拟）
    device: load('jwt_device_v1', { battery: 97, signal: 4, worn: true }),

    get effectiveOnline() { return this.online && !this.simOffline; },

    saveDevice() { save('jwt_device_v1', this.device); },
    setDevice(patch) {
      Object.assign(this.device, patch);
      this.saveDevice();
      this.emit();
    },

    onChange(fn) { this.listeners.push(fn); },
    /** 视图级监听：重复进入页面时替换旧回调，避免叠加触发已销毁视图 */
    setViewListener(fn) { this.viewListener = fn; },
    emit() {
      const snap = this.snapshot();
      this.listeners.forEach((fn) => { try { fn(snap); } catch {} });
      if (this.viewListener) { try { this.viewListener(snap); } catch {} }
    },
    snapshot() {
      return {
        online: this.effectiveOnline,
        realOnline: this.online,
        simOffline: this.simOffline,
        queueCount: this.queue.length,
        logs: this.logs.slice(0, 30),
        syncing: this.syncing,
        device: Object.assign({}, this.device),
      };
    },

    setSimOffline(v) {
      this.simOffline = !!v;
      if (this.simOffline) {
        this.log('OFFLINE', '已模拟乡村弱网/断网场景：定位转入本地缓存');
      } else {
        this.log('SYNC', '模拟网络恢复，开始合并补传…');
        this.sync();
      }
      this.emit();
    },

    init() {
      const setOnline = (online) => {
        const was = this.online;
        this.online = online;
        this.log(online ? 'SYNC' : 'OFFLINE', online ? '网络已恢复' : '网络断开，进入离线模式');
        if (!was && online) {
          this.log('SYNC', '开始合并补传离线队列…');
          this.sync();
        }
        this.emit();
      };
      window.addEventListener('online', () => setOnline(true));
      window.addEventListener('offline', () => setOnline(false));

      // 兜底心跳：onLine 偶发不准（有信号无网），失败的请求也会标离线
      setInterval(() => { if (navigator.onLine && this.queue.length) this.sync(); }, 20000);
      this.emit();
    },

    log(type, msg) {
      const time = new Date().toLocaleTimeString('zh-CN', { hour12: false });
      this.logs.unshift({ time, type, msg });
      this.logs = this.logs.slice(0, 50);
      save(LOG_KEY, this.logs);
    },

    /**
     * 采集一个定位帧（每 5 秒）；offlineCaptured 按“采集瞬间是否在线”如实标记。
     * 帧内携带设备状态电量/信号/佩戴。
     */
    capture(lat, lng, fixAgeSeconds) {
      const now = new Date();
      const point = {
        clientPointId: uuid(),
        pointTime: utcIso(now),
        lat: Number(lat.toFixed(6)),
        lng: Number(lng.toFixed(6)),
        offlineCaptured: !this.effectiveOnline,
        battery: this.device.battery,
        signal: this.device.signal,
        worn: this.device.worn,
      };
      this.queue.push(point);
      save(QUEUE_KEY, this.queue);
      const where = this.effectiveOnline ? '在线实时点，待上报' : '离线缓存点';
      this.log(this.effectiveOnline ? 'OK' : 'OFFLINE',
        `采集定位（${where}）${point.lat.toFixed(4)},${point.lng.toFixed(4)}`
          + (fixAgeSeconds != null ? `，定位龄 ${fixAgeSeconds}s` : '')
          + `｜🔋${this.device.battery}% 📶${this.device.signal} ${this.device.worn ? '已佩戴' : '未佩戴'}`);
      this.emit();
      if (this.effectiveOnline) this.sync();
      return point;
    },

    /** 整队列补传；幂等/漂移质检由服务端裁决，重放安全 */
    async sync() {
      if (this.syncing || this.queue.length === 0) return;
      if (!navigator.onLine || this.simOffline) { this.emit(); return; }
      this.syncing = true; this.online = true; this.emit();

      // 复制一份发送：成功后按 clientPointId 移除；服务端拒绝的旧点不再重放（永久拒绝）
      const batch = this.queue.slice();
      const ids = new Set(batch.map((p) => p.clientPointId));
      try {
        const result = await Api.post('/offender/tracks', { points: batch });
        const remaining = this.queue.filter((p) => !ids.has(p.clientPointId));
        this.queue = remaining;
        save(QUEUE_KEY, this.queue);

        if (result.duplicates > 0) {
          this.log('DUP', `合并补传完成：${result.duplicates} 个重复点被幂等去重，未产生重复轨迹`);
        }
        if (result.driftDiscarded > 0) {
          this.log('DRIFT', `${result.driftDiscarded} 个点连续跳变速度异常，服务端判 GPS 漂移丢弃（不连线、不报警）`);
        }
        if (result.rejectedPoints && result.rejectedPoints.length) {
          result.rejectedPoints
            .filter((r) => !/漂移/.test(r.reason))
            .forEach((r) =>
            this.log('REJECT', `旧位置点 ${r.clientPointId.slice(0, 8)} 被服务端拒绝：${r.reason}`));
        }
        if (result.accepted > 0) {
          this.log('OK', `补传成功：${result.accepted} 个轨迹点已按采集时间(UTC)合并入库`
            + (result.outsideFence ? `，其中 ${result.outsideFence} 个点越界` : '')
            + (result.forbidden ? `，${result.forbidden} 个点进入禁区` : '')
            + (result.newViolationGenerated ? '，已生成预警' : ''));
        }
      } catch (e) {
        if (e.offline || e.code === 'NETWORK_OFFLINE') {
          this.online = false;
          this.log('OFFLINE', '补传失败：网络实际不可用，继续保留本地队列（' + batch.length + ' 点）');
        } else {
          this.log('REJECT', '补传失败：' + e.message + '，保留队列待重试');
        }
      } finally {
        this.syncing = false;
        this.emit();
      }
    },

    clearAll() {
      this.queue = [];
      save(QUEUE_KEY, this.queue);
      this.emit();
    },
  };

  global.TrackQueue = TrackQueue;
  global.localIso = utcIso;
})(window);
