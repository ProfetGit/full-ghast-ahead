// Full Ghast Ahead icon scene + animation (pack-icon-animation skill). Run inside Blockbench (free format project):
//   eval(require('fs').readFileSync('<this file>', 'utf8')); window.FGA = FGA; FGA.loadTextures()   // then, in a later call:
//   FGA.build(); FGA.animate(); FGA.camera()                                                        // then:
//   FGA.render(0, 63)                                                                                // 1600px frames -> frames/
// Other sessions may drive the same Blockbench: every entry point re-selects this pack's project (uuid, else name), the
// global is `FGA` (never `VM`), and render() holds window.BB_LOCK while it runs and refuses to start under someone else's.
// A chill cruise: the ghast bobs, leans into the flight and waves its tentacles. The clouds that pass by are composited in
// dev/make_icon.py and dev/make_banner.py (scrolling on the background's pixel grid), not rendered here. The loop is 64 frames,
// so a cloud layer moving 1 background pixel per frame wraps a 64px tile exactly.
// The ghast lives in `yaw` (rotated 45 deg: west = left face, south = right face = its face) and faces yaw +z, screen right.
// Rig: yaw > fly (position) > lean (rotation about x) > squash (scale) > body, goggles, tentacles.
// Positive lean tips the top forward (+z); positive tentacle swing trails the tips back (-z).
// Screen space: the `screen` group is tilted to face the orthographic camera, so inside it x = right, y = up, z = toward camera.
var FGA = (function () {
  const fs = require('fs');
  const DIR = '/home/emppu/Projects/Minecraft Datapacks/FullGhastAhead/dev/icon/';
  const TEX = DIR + 'sprites/';
  const PROJECT = { uuid: '85825bb6-ef87-8c9f-36a7-ab0d0a301ff9', name: 'full_ghast_ahead_icon_anim' };
  const LOCK_OWNER = 'full-ghast-ahead';
  const FPS = 25, DT = 1 / FPS, FRAMES = 64, LEN = FRAMES / FPS;
  const CAM_POS = [0, 60, 104], CAM_TARGET = [0, 16, 0], CAM_PAN = [0, -3, 0], CAM_ZOOM = 0.6;
  const PITCH = -Math.atan2(CAM_POS[1] - CAM_TARGET[1], CAM_POS[2] - CAM_TARGET[2]) * 180 / Math.PI;
  const O = new THREE.Vector3(...CAM_TARGET);
  const RAD = Math.PI / 180;
  const Z_FX = 50;

  const C = [0, 16, 0];                               // body centre (lean + squash pivot)
  const TENT = [[-5, -5, 5], [0, -5, 7], [5, -5, 4], [-5, 0, 6], [0, 0, 5], [5, 0, 7], [-5, 5, 4], [0, 5, 6], [5, 5, 5]];
  const GOG = { from: [-6, 19, 8], to: [6, 24, 9.5] };

  // cruise: one slow bob per loop, tentacles wave twice per loop as a wave running front to back
  const P = {
    bob: 1.2, lean: 4, leanWobble: 1.5,
    trail: 16, wave: 10, waves: 2, rowPhase: 0.9, colPhase: 0.35,
    glint: 1.60,
  };

  const q = t => Math.round(t * FPS) / FPS;
  const worldToScreen = w => new THREE.Vector3(...w).sub(O).applyEuler(new THREE.Euler(-PITCH * RAD, 0, 0)).add(O);
  const yawToWorld = a => new THREE.Vector3(...a).applyEuler(new THREE.Euler(0, 45 * RAD, 0));
  const yawToScreen = a => worldToScreen(yawToWorld(a).toArray());
  const phase = t => 2 * Math.PI * t / LEN;
  const bobAt = t => P.bob * Math.sin(phase(t));

  function own() {
    const p = ModelProject.all.find(p => p.uuid === PROJECT.uuid) || ModelProject.all.find(p => p.name === PROJECT.name);
    if (!p) throw new Error('project ' + PROJECT.name + ' is not open');
    if (Project !== p) p.select();
    return p;
  }

  const tex = {};
  function loadTextures() {
    own();
    Texture.all.slice().forEach(t => t.remove(true));
    for (const f of fs.readdirSync(TEX).filter(f => f.endsWith('.png') && !/^(bg|banner|cloud)_/.test(f))) {
      const url = 'data:image/png;base64,' + fs.readFileSync(TEX + f).toString('base64');
      tex[f.slice(0, -4)] = new Texture({ name: f }).fromDataURL(url).add(false);
    }
    return Object.keys(tex).join(',');
  }
  function ensureTex() {
    if (!Object.keys(tex).length) Texture.all.forEach(t => { tex[t.name.replace('.png', '')] = t; });
  }

  function group(name, origin, parent, rotation) {
    const g = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
    g.addTo(parent); g.init();
    return g;
  }
  const FACES = ['north', 'south', 'east', 'west', 'up', 'down'];
  function cube(name, from, to, parent, faceTex, opts) {
    const c = new Cube(Object.assign({ name, from, to, box_uv: false }, opts || {}));
    c.addTo(parent); c.init();
    for (const f of FACES) {
      const spec = faceTex[f] || faceTex.all;
      if (spec) c.faces[f].extend({ texture: tex[spec[0]].uuid, uv: spec[1] });
      else c.faces[f].extend({ texture: null });
    }
    return c;
  }
  function plane(name, centre, size, parent, texName, uvSize, mirror) {
    const [x, y, z] = centre, h = size / 2;
    const uv = mirror ? [uvSize, 0, 0, uvSize] : [0, 0, uvSize, uvSize];
    return cube(name, [x - h, y - h, z], [x + h, y + h, z], parent, { south: [texName, uv] });
  }

  const FULL = [0, 0, 16, 16];
  const G = {};
  function build() {
    own(); ensureTex();
    Animation.all.slice().forEach(a => a.remove(false));
    Outliner.root.slice().forEach(n => n.remove(false));

    G.yaw = group('yaw', [0, 0, 0], undefined, [0, 45, 0]);
    G.fly = group('fly', C, G.yaw);
    G.lean = group('lean', C, G.fly);
    G.squash = group('squash', C, G.lean);
    cube('body', [-8, 8, -8], [8, 24, 8], G.squash, {
      south: ['ghast_face_s', FULL], west: ['ghast_side_w', FULL], up: ['ghast_top', FULL], down: ['ghast_side_w', FULL],
    });
    G.goggles = group('goggles', [0, 21.5, 8.75], G.squash);
    cube('goggles_box', GOG.from, GOG.to, G.goggles, {
      south: ['goggles_s', [0, 0, 12, 5]], west: ['goggle_edge_w', [0, 0, 1.5, 5]], up: ['goggle_edge', [0, 0, 12, 1.5]],
    });
    TENT.forEach(([x, z, L], i) => {
      const g = G['tent_' + i] = group('tent_' + i, [x, 8, z], G.squash);
      cube('tentacle_' + i, [x - 1, 8 - L, z - 1], [x + 1, 8, z + 1], g, {
        west: ['tentacle_w', [0, 0, 2, L]], south: ['tentacle_s', [0, 0, 2, L]], down: ['tentacle_s', [0, L - 2, 2, L]],
      });
    });

    G.screen = group('screen', O.toArray(), undefined, [PITCH, 0, 0]);
    const p = lensGlint();
    G.glint = group('glint', [p.x, p.y, Z_FX], G.screen);
    plane('glint_plane', [p.x, p.y, Z_FX], 8, G.glint, 'fx_spark', 16);

    Canvas.updateAll();
    return Outliner.elements.length;
  }
  const lensGlint = () => yawToScreen([-3, 22.5, 9.6]);

  // ---- animation ----
  let A = null;
  function K(g, ch, t, v, interp) {
    const [x, y, z] = typeof v === 'number' ? [v, v, v] : v;
    A.getBoneAnimator(g).addKeyframe({ channel: ch, time: q(t), interpolation: interp || 'linear', data_points: [{ x, y, z }] });
  }
  const track = (g, ch, keys, interp) => keys.forEach(([t, v, i]) => K(g, ch, t, v, i || interp));
  const rx = a => [a, 0, 0];
  // one key per frame (0..FRAMES inclusive, so t = 0 and t = LEN match exactly)
  const sampled = f => Array.from({ length: FRAMES + 1 }, (_, i) => [i * DT, f(i * DT)]);

  function ensureG() {
    if (!Object.keys(G).length) Group.all.forEach(g => { G[g.name] = g; });
  }
  function animate() {
    own(); ensureTex(); ensureG();
    Animation.all.slice().forEach(a => a.remove(false));
    A = new Animation({ name: 'icon_loop', length: LEN, loop: 'loop', snapping: FPS }).add(false);
    A.select();

    track(G.fly, 'position', sampled(t => [0, bobAt(t), 0]));
    // lean a little more into the flight while sinking, less while rising
    track(G.lean, 'rotation', sampled(t => rx(P.lean + P.leanWobble * Math.cos(phase(t)))));
    TENT.forEach(([x, z], i) => {
      const lag = (5 - z) / 5 * P.rowPhase + (x + 5) / 5 * P.colPhase;
      track(G['tent_' + i], 'rotation', sampled(t => rx(P.trail + P.wave * Math.sin(P.waves * phase(t) - lag))));
    });
    // one lens twinkle per loop; the spark rides the bob (screen y = world y * cos(pitch))
    const t0 = P.glint, k = Math.cos(PITCH * RAD);
    track(G.glint, 'scale', [[0, 0], [t0, 0], [t0 + 0.04, 0.6], [t0 + 0.08, 1.2], [t0 + 0.12, 0.7], [t0 + 0.16, 0]]);
    track(G.glint, 'rotation', [[t0, [0, 0, 0]], [t0 + 0.16, [0, 0, 45]]]);
    track(G.glint, 'position', [0, 0.04, 0.08, 0.12, 0.16].map(d => [t0 + d, [0, bobAt(t0 + d) * k, 0]]));

    Animator.preview();
    return 'cruise ' + FRAMES + ' frames';
  }

  // ---- camera + render ----
  function camera(zoom) {
    own();
    const p = Preview.selected;
    p.setProjectionMode(true);
    const pos = CAM_POS.map((v, i) => v + CAM_PAN[i]), tgt = CAM_TARGET.map((v, i) => v + CAM_PAN[i]);
    p.camera.position.set(...pos);
    p.controls.target.set(...tgt);
    p.camera.lookAt(...tgt);
    p.camera.zoom = zoom || CAM_ZOOM; p.camera.updateProjectionMatrix();
    p.controls.update();
  }
  function setTime(t) {
    Timeline.setTime(t);
    Animator.preview();
  }
  function render(first, last, res, dir) {
    const lock = window.BB_LOCK;
    if (lock && lock.owner !== LOCK_OWNER && lock.until > Date.now()) return 'locked by ' + lock.owner + ' for ' + Math.round((lock.until - Date.now()) / 1000) + ' s';
    window.BB_LOCK = { owner: LOCK_OWNER, until: Date.now() + 120000 };
    own();
    res = res || 1600;
    dir = dir || DIR + 'frames/';
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    const shot = f => new Promise(done => {
      setTime(f * DT);
      Screencam.advancedScreenshot(Preview.selected, { angle_preset: 'view', resolution: [res, res], anti_aliasing: 'none', shading: false }, url => {
        fs.writeFileSync(dir + 'frame_' + String(f).padStart(3, '0') + '.png', Buffer.from(url.split(',')[1], 'base64'));
        done();
      });
    });
    return (async () => {
      try { for (let f = first; f <= last; f++) await shot(f); } finally { window.BB_LOCK = null; }
      return `rendered ${first}..${last} into ${dir}`;
    })();
  }
  function scaleSweep() {
    own();
    const bad = [];
    for (let f = 0; f <= FRAMES; f++) {
      setTime(f * DT);
      Group.all.forEach(g => { const s = g.mesh.scale; if (s.x < 0 || s.y < 0 || s.z < 0) bad.push(g.name + '@' + f); });
    }
    return bad.length ? bad.join(',') : 'no negative scale';
  }

  return { FPS, DT, FRAMES, LEN, P, PITCH, G, tex, own, loadTextures, build, animate, camera, render, setTime, scaleSweep, worldToScreen, yawToWorld, yawToScreen, q };
})();
