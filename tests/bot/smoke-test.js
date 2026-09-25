'use strict';
/*
 * OnlineTimeStats 真机冒烟测试
 *
 * 流程：安装插件 -> 启动 Paper 1.12.2 -> mineflayer 机器人以真实客户端身份进服
 * -> 核对计时、排行、管理指令、重载、记分板、挂机判定与数据文件 -> 停服。
 *
 * 用法： node smoke-test.js <测试服目录> <java路径> <插件jar> [输出目录]
 */

const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');

const serverDir = path.resolve(process.argv[2]);
const javaExe = process.argv[3];
const jarPath = path.resolve(process.argv[4]);
const outDir = path.resolve(process.argv[5] || path.join(__dirname, 'out'));

const HOST = '127.0.0.1';
const PORT = 25599;
const BOT_ONE = 'TestBot';
const BOT_TWO = 'TestBot2';
const ONLINE_MS = 70000;
const AFK_TEST_MS = 60000;
const AFK_THRESHOLD_SECONDS = 15;
const AFK_MAX_SECONDS = 35;

const results = [];
const check = (name, ok, detail) => {
  results.push({ name, ok });
  console.log(`${ok ? '[通过]' : '[失败]'} ${name}${detail ? '  | ' + detail : ''}`);
};
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const stripAnsi = (text) => text.replace(/\x1B\[[0-9;]*[A-Za-z]/g, '');

const decUtf8 = new TextDecoder('utf-8');
let decGbk = null;
try {
  decGbk = new TextDecoder('gbk');
} catch (e) {
  decGbk = null;
}

let chunks = [];
let server = null;
const bots = new Map();
let botEvents = [];
const pluginDir = path.join(serverDir, 'plugins');
const dataFile = path.join(pluginDir, 'OnlineTimeStats', 'data', 'playtime.tsv');
const configFile = path.join(pluginDir, 'OnlineTimeStats', 'config.yml');

async function main() {
  if (!fs.existsSync(path.join(serverDir, 'server.jar'))) {
    throw new Error('测试服目录缺少 server.jar: ' + serverDir);
  }
  fs.mkdirSync(pluginDir, { recursive: true });
  if (fs.existsSync(path.join(pluginDir, 'OnlineTimeStats'))) {
    fs.rmSync(path.join(pluginDir, 'OnlineTimeStats'), { recursive: true, force: true });
  }
  for (const file of fs.readdirSync(pluginDir)) {
    if (file.endsWith('.jar')) {
      fs.unlinkSync(path.join(pluginDir, file));
    }
  }
  fs.copyFileSync(jarPath, path.join(pluginDir, path.basename(jarPath)));
  console.log('已安装插件: ' + path.basename(jarPath));

  server = spawn(javaExe, [
    '-Xmx1200M',
    '-Dfile.encoding=UTF-8',
    '-Dsun.stdout.encoding=UTF-8',
    '-Dsun.stderr.encoding=UTF-8',
    '-jar', 'server.jar', 'nogui',
  ], { cwd: serverDir, stdio: ['pipe', 'pipe', 'pipe'] });
  server.stdout.on('data', (buf) => chunks.push(buf));
  server.stderr.on('data', (buf) => chunks.push(buf));
  server.on('exit', (code) => console.log('服务器进程结束，退出码 ' + code));

  check('服务器启动完成', await waitFor(/Done \(/, 180000));
  if (!server) {
    return finish();
  }
  const startup = text();
  check('插件被加载并启用', /Enabling OnlineTimeStats v1\.0\.0/.test(startup));
  check('启动日志没有报错', !/(Could not load|Caused by|Exception|SEVERE)/.test(startup),
      firstMatch(startup, /(Could not load.*|Caused by.*|Exception.*)/));

  // 测试环境控制：保持白天、不刷怪，避免怪物把机器人推得到处跑（那会被视为“有操作”）
  await cmd('time set day', 1000);
  await cmd('gamerule doMobSpawning false', 1000);
  await cmd('difficulty peaceful', 1000);

  let out = await cmd('playtime', 1500);
  check('控制台执行 /playtime 输出用法', /playtime/.test(out.utf8 + out.gbk));

  out = await cmd('playtime top', 1500);
  check('空数据时排行提示为空', /top|记录/.test(out.utf8 + out.gbk));

  // ---- 机器人 1 进服，核对记分板 ----
  const botOne = createBot(BOT_ONE);
  check('机器人 1 成功进服', await waitBot(() => hasEvent(BOT_ONE, 'spawn'), 60000));
  await sleep(3000);

  out = await cmd('scoreboard objectives list', 1500);
  check('服务器已创建在线时间 objective',
      /playtime/.test(out.utf8 + out.gbk), firstMatch(out.utf8 + out.gbk, /- playtime.*/));

  let text2 = await status();
  check('status 读回“名字下方”显示位置', /below_name=playtime\b/.test(text2),
      firstMatch(text2, /显示位置.*/));
  check('status 读回 Tab 列表显示位置（镜像目标）', /list=playtime_list\b/.test(text2),
      firstMatch(text2, /显示位置.*/));
  check('status 读回数据文件路径', /playtime\.tsv/.test(text2), firstMatch(text2, /数据文件.*/));

  // ---- 重载 ----
  out = await cmd('playtime reload', 2500);
  check('reload 指令可用', /已重载|重载/.test(out.utf8 + out.gbk), firstMatch(out.utf8 + out.gbk, /.*重载.*/));
  await sleep(1500);
  text2 = await status();
  check('reload 后记分板显示位置仍然正确',
      /below_name=playtime\b/.test(text2) && /list=playtime_list\b/.test(text2),
      firstMatch(text2, /显示位置.*/));
  out = await cmd(`scoreboard players list ${BOT_ONE}`, 2000);
  check('reload 后在线玩家分数仍在', /playtime/.test(out.utf8 + out.gbk));

  // ---- 机器人 2 进服：验证“后来进服的玩家”也能看到名字下方的时长 ----
  createBot(BOT_TWO);
  check('机器人 2 成功进服', await waitBot(() => hasEvent(BOT_TWO, 'spawn'), 60000));
  await sleep(4000);
  check('后来进服的玩家收到 belowName 显示位置', positionsFor(BOT_TWO).includes('2'),
      BOT_TWO + ' 收到位置: ' + positionsFor(BOT_TWO).join(','));
  const botTwo = bots.get(BOT_TWO);
  if (botTwo) {
    botTwo.quit();
    await sleep(2000);
  }

  // ---- 在线计时 ----
  console.log(`保持在线 ${ONLINE_MS / 1000} 秒 …`);
  await sleep(ONLINE_MS);

  out = await cmd(`playtime check ${BOT_ONE}`, 2500);
  const info = out.utf8 + '\n' + out.gbk;
  check('可以查询到在线玩家', new RegExp(BOT_ONE).test(info));
  check('在线时长已累计到分钟级', /1\u5206\u949f|2\u5206\u949f/.test(info),
      firstMatch(info, /\u603b\u65f6\u957f.*/));

  out = await cmd('playtime top', 2000);
  check('排行榜包含该玩家', new RegExp(BOT_ONE).test(out.utf8 + out.gbk));

  // ---- 管理指令与记分板分数 ----
  await cmd(`playtime set ${BOT_ONE} 2h30m`, 2500);
  await sleep(7000);
  out = await cmd(`scoreboard players list ${BOT_ONE}`, 2500);
  // 结构化断言只用 UTF-8 解码结果，避免把 GBK 回退解码的重复行也算进来
  const scoreLines = out.utf8.split(/\r?\n/).filter((line) => line.includes('playtime'));
  check('主目标与镜像目标都按小时写入分数（2h30m -> 2）',
      scoreLines.length === 2 && scoreLines.every((l) => /\b2\b/.test(l)),
      scoreLines.join(' / '));

  out = await cmd('playtime check Notch_NotExist', 2000);
  check('不存在的玩家给出提示', /Notch_NotExist/.test(out.utf8 + out.gbk));

  out = await cmd('playtime stats', 2000);
  check('stats 输出全服概览', /2/.test(out.utf8 + out.gbk));

  // ---- 退出后仍可查询与修改 ----
  botOne.quit();
  await sleep(6000);
  out = await cmd('playtime top', 2000);
  check('退出后排行仍包含该玩家', new RegExp(BOT_ONE).test(out.utf8 + out.gbk));

  await cmd(`playtime add ${BOT_ONE} 30m`, 2500);
  out = await cmd(`playtime check ${BOT_ONE}`, 2500);
  const offlineInfo = out.utf8 + '\n' + out.gbk;
  check('离线玩家可查询且累加生效（2h30m + 30m -> 3 小时）',
      /3\u5c0f\u65f6/.test(offlineInfo), firstMatch(offlineInfo, /\u603b\u65f6\u957f.*/));
  check('离线玩家状态显示为离线', /离线/.test(offlineInfo));

  // ---- 数据文件 ----
  await sleep(1500);
  check('数据文件已生成', fs.existsSync(dataFile), dataFile);
  const content = fs.existsSync(dataFile) ? fs.readFileSync(dataFile, 'utf8') : '';
  const botOneLine = content.split(/\r?\n/).find((line) => line.includes('\t' + BOT_ONE + '\t'));
  const botTwoLine = content.split(/\r?\n/).find((line) => line.includes('\t' + BOT_TWO + '\t'));
  check('数据文件包含两个玩家的记录', !!botOneLine && !!botTwoLine);
  if (botOneLine) {
    const fields = botOneLine.split('\t');
    check('时长按 tick 落盘且不少于 3 小时', parseInt(fields[2], 10) >= 3 * 3600 * 20, fields[2] + ' ticks');
    check('登录次数与首次加入时间已记录', fields[7] === '1' && parseInt(fields[4], 10) > 0,
        'sessions=' + fields[7]);
  }

  // ---- 挂机判定：开启后站着不动就不再累计 ----
  await cmd(`playtime reset ${BOT_TWO}`, 2000);
  check('测试用配置修改成功', enableAfkInConfig());
  await cmd('playtime reload', 2500);
  text2 = await status();
  check('重载后挂机判定已开启且阈值生效',
      /挂机判定:\s*开\(\s*15s/.test(text2), firstMatch(text2, /挂机判定.*/));

  const afkBot = createBot(BOT_TWO);
  const afkSpawned = await waitBot(() => hasEvent(BOT_TWO, 'spawn'), 60000);
  check('挂机测试用机器人进服', afkSpawned);
  await sleep(3000);
  await cmd('time set day', 1000);
  const startPos = afkBot && afkBot.entity ? afkBot.entity.position.clone() : null;
  console.log(`挂机测试：站着不动 ${AFK_TEST_MS / 1000} 秒 …`);
  await sleep(AFK_TEST_MS);
  const endPos = afkBot && afkBot.entity ? afkBot.entity.position.clone() : null;
  const drift = startPos && endPos ? startPos.distanceTo(endPos) : -1;
  out = await cmd(`playtime check ${BOT_TWO}`, 2500);
  const afkSeconds = totalSecondsOf(out.utf8 + '\n' + out.gbk);
  check(`挂机后停止累计（阈值 ${AFK_THRESHOLD_SECONDS}s，实际累计 ${afkSeconds}s，上限 ${AFK_MAX_SECONDS}s，机器人位移 ${drift.toFixed(2)} 格）`,
      afkSeconds >= 5 && afkSeconds <= AFK_MAX_SECONDS,
      firstMatch(out.utf8 + out.gbk, /\u603b\u65f6\u957f.*/));

  // ---- 额外细粒度目标（extra-objectives） ----
  out = await cmd('scoreboard objectives list', 2000);
  const objectives = ['ots_tick', 'ots_seconds', 'ots_minutes', 'ots_hours', 'ots_total'];
  const missing = objectives.filter((name) => !out.utf8.includes(name));
  check('开启 extra-objectives 后创建 5 个额外目标', missing.length === 0,
      missing.length ? '缺少: ' + missing.join(',') : objectives.join(', '));
  out = await cmd(`scoreboard players list ${BOT_TWO}`, 2000);
  const extraTracked = ['ots_seconds', 'ots_minutes', 'ots_hours', 'ots_total']
      .filter((name) => out.utf8.includes('(' + name + ')'));
  check('额外目标也写入了在线玩家分数', extraTracked.length === 4,
      '已写入: ' + extraTracked.join(', '));

  if (afkBot) {
    afkBot.quit();
  }
  await sleep(3000);

  // ---- 重置 ----
  out = await cmd('playtime reset all confirm', 2500);
  check('reset all 清除全部记录', /清除/.test(out.utf8 + out.gbk), firstMatch(out.utf8 + out.gbk, /.*清除.*/));
  await sleep(2000);
  const afterReset = fs.existsSync(dataFile) ? fs.readFileSync(dataFile, 'utf8') : '';
  check('重置后数据文件不再有玩家记录',
      afterReset.split(/\r?\n/).filter((l) => l.trim() && !l.startsWith('#')).length === 0);
  out = await cmd('playtime top', 2000);
  check('重置后排行榜为空', /还没有|top/i.test(out.utf8 + out.gbk));

  await shutdown();
  check('机器人会话正常结束', botEvents.some((e) => e.type === 'end'));
  return finish();
}

/** 把 afk.enabled 改成 true、阈值改成 15 秒，并开启 extra-objectives，用来做端到端测试。 */
function enableAfkInConfig() {
  if (!fs.existsSync(configFile)) {
    return false;
  }
  const original = fs.readFileSync(configFile, 'utf8');
  const index = original.indexOf('\nafk:');
  if (index < 0) {
    return false;
  }
  const head = original.slice(0, index);
  const tail = original.slice(index);
  const patched = tail
      .replace('enabled: false', 'enabled: true')
      .replace('threshold-seconds: 300', 'threshold-seconds: ' + AFK_THRESHOLD_SECONDS)
      .replace('extra-objectives: false', 'extra-objectives: true');
  if (patched === tail) {
    return false;
  }
  fs.writeFileSync(configFile, head + patched, 'utf8');
  return true;
}

/** 从 /playtime check 的输出里取出总秒数。 */
function totalSecondsOf(output) {
  const match = /\u5171\s*(\d+)\s*\u5929\s*(\d+)\s*\u5c0f\u65f6\s*(\d+)\s*\u5206\u949f\s*\/\s*(\d+)\s*\u79d2/.exec(output);
  if (!match) {
    return -1;
  }
  return Number(match[1]) * 86400 + Number(match[2]) * 3600 + Number(match[3]) * 60 + Number(match[4]);
}

async function status() {
  const out = await cmd('playtime status', 2000);
  return out.utf8 + '\n' + out.gbk;
}

function createBot(name) {
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username: name,
    version: '1.12.2',
    auth: 'offline',
  });
  bots.set(name, bot);
  const push = (event) => {
    event.bot = name;
    botEvents.push(event);
  };
  bot.on('spawn', () => push({ type: 'spawn' }));
  bot.on('scoreboardCreated', (sb) => push({ type: 'scoreboardCreated', scoreboard: sb.name }));
  bot.on('scoreboardPosition', (position, sb) =>
    push({ type: 'scoreboardPosition', position: String(position), scoreboard: sb && sb.name }));
  bot.on('message', (json) => push({ type: 'message', text: json.toString() }));
  bot.on('error', (err) => push({ type: 'error', error: err.message }));
  bot.on('end', (reason) => push({ type: 'end', reason: String(reason) }));
  return bot;
}

function hasEvent(botName, type) {
  return botEvents.some((e) => e.bot === botName && e.type === type);
}

function positionsFor(botName) {
  return botEvents
    .filter((e) => e.bot === botName && e.type === 'scoreboardPosition')
    .map((e) => e.position);
}

function waitBot(predicate, timeoutMs) {
  return new Promise((resolve) => {
    const deadline = Date.now() + timeoutMs;
    const timer = setInterval(() => {
      if (predicate()) {
        clearInterval(timer);
        resolve(true);
      } else if (Date.now() > deadline) {
        clearInterval(timer);
        resolve(false);
      }
    }, 500);
  });
}

function text() {
  return stripAnsi(decUtf8.decode(Buffer.concat(chunks)));
}

function firstMatch(haystack, regex) {
  const found = regex.exec(haystack);
  return found ? found[0].trim() : '';
}

function waitFor(regex, timeoutMs) {
  return new Promise((resolve) => {
    const deadline = Date.now() + timeoutMs;
    const timer = setInterval(() => {
      if (regex.test(text())) {
        clearInterval(timer);
        resolve(true);
      } else if (Date.now() > deadline) {
        clearInterval(timer);
        resolve(false);
      }
    }, 500);
  });
}

async function cmd(command, waitMs) {
  const mark = chunks.length;
  server.stdin.write(command + '\n');
  console.log('> ' + command);
  await sleep(waitMs || 1500);
  const buf = Buffer.concat(chunks.slice(mark));
  const utf8 = stripAnsi(decUtf8.decode(buf));
  const gbk = decGbk ? stripAnsi(decGbk.decode(buf)) : '';
  console.log(utf8.split(/\r?\n/).filter((l) => l.trim()).join('\n'));
  return { utf8, gbk };
}

async function shutdown() {
  for (const bot of bots.values()) {
    try {
      bot.quit();
    } catch (e) {
      // 忽略
    }
  }
  bots.clear();
  if (server) {
    try {
      server.stdin.write('stop\n');
    } catch (e) {
      // 忽略
    }
    const stopped = await new Promise((resolve) => {
      const timer = setTimeout(() => resolve(false), 60000);
      server.on('exit', () => {
        clearTimeout(timer);
        resolve(true);
      });
    });
    if (!stopped) {
      server.kill('SIGKILL');
    }
    server = null;
  }
  await sleep(1500);
}

function finish() {
  if (!fs.existsSync(outDir)) {
    fs.mkdirSync(outDir, { recursive: true });
  }
  fs.writeFileSync(path.join(outDir, 'smoke-server.log'), text(), 'utf8');
  fs.writeFileSync(path.join(outDir, 'smoke-bot-events.log'),
      botEvents.map((e) => JSON.stringify(e)).join('\n'), 'utf8');
  const failed = results.filter((r) => !r.ok);
  const lines = results.map((r) => `${r.ok ? '[通过]' : '[失败]'} ${r.name}`);
  lines.push('');
  lines.push(`通过: ${results.length - failed.length}，失败: ${failed.length}`);
  fs.writeFileSync(path.join(outDir, 'smoke-report.txt'), lines.join('\n'), 'utf8');
  console.log('');
  console.log(`通过: ${results.length - failed.length}，失败: ${failed.length}`);
  failed.forEach((f) => console.log('  失败项: ' + f.name));
  process.exit(failed.length > 0 ? 1 : 0);
}

main().catch(async (err) => {
  console.error('测试异常: ' + (err && err.stack ? err.stack : err));
  try {
    await shutdown();
  } catch (e) {
    // 忽略
  }
  check('测试未发生异常', false, String(err && err.message ? err.message : err));
  finish();
});
