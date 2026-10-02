'use strict';
// The tester groups, in LuckPerms (with Vault, which is how Wormhole finds it): visitor, builder
// and operator, each the one before plus more of Wormhole's nodes. None is a server op: an op
// passes every Wormhole check, and WorldGuard's region bypass with it.
//
// LuckPerms answers its commands from another thread, after the console command that asked has
// returned, so every command here waits for its own answer in the log rather than reading what
// the command printed.

const USE = ['wormhole.use.sign', 'wormhole.use.dialer', 'wormhole.use.compass', 'wormhole.list',
  'wormhole.ring.use', 'wormhole.beam.use', 'wormhole.beam.place'];
const BUILD = ['wormhole.build', 'wormhole.build.preview', 'wormhole.build.preview.share', 'wormhole.build.preview.place',
  'wormhole.remove.own', 'wormhole.ring.build', 'wormhole.go'];
const ADMIN = ['wormhole.config', 'wormhole.remove.all', 'wormhole.ring.admin', 'wormhole.ring.unlimited',
  'wormhole.beam.admin', 'wormhole.beam.admin.teleport'];

/** Each group: what it is for, the group it inherits, and the nodes it adds. */
const GROUPS = {
  visitor: { why: 'use gates, rings, beams and mirrors, no building', parent: null, nodes: USE },
  builder: { why: 'a visitor who may also build gates and rings, by hand and by preview', parent: 'visitor', nodes: BUILD },
  operator: { why: 'a builder who may also configure and manage everything, without being a server op', parent: 'builder', nodes: ADMIN },
};

/** Probe2's group whenever a chamber has not put it in another: the player the facility means. */
const BASELINE = 'visitor';

/** Every node a group holds, its parents' included. */
function nodesOf(group) {
  const g = GROUPS[group];
  if (!g) return [];
  return [...nodesOf(g.parent), ...g.nodes];
}

/**
 * A matcher for a LuckPerms answer line: "[LP] " and then any of `texts`, compared in lower case
 * (LuckPerms lower-cases player names). Plain text, never a regular expression built from a name.
 */
function lpSaid(...texts) {
  const want = texts.map((t) => `[lp] ${t}`.toLowerCase());
  return { test: (line) => { const l = line.toLowerCase(); return want.some((t) => l.includes(t)); } };
}

class Groups {
  constructor(srv) {
    this.srv = srv;
  }

  /**
   * Runs a LuckPerms command and waits (bounded) for the log line that is its answer; returns
   * that line. The wait is set up before the command is sent, so the answer cannot be missed.
   */
  async lp(command, answer, what = command, ms = 15000) {
    const heard = this.srv.waitFor(answer, ms, `LuckPerms to answer ${what}`);
    try {
      await this.srv.run(command);
    } catch (e) {
      heard.catch(() => {});
      throw e;
    }
    return heard;
  }

  /** Creates the three groups (again, if they are there) with exactly their nodes. */
  async ensure() {
    for (const [name, g] of Object.entries(GROUPS)) {
      await this.lp(`lp creategroup ${name}`, lpSaid(`${name} was successfully created`, `${name} already exists`));
      await this.lp(`lp group ${name} permission clear`, lpSaid(`${name}'s permissions were cleared`, `${name} does not have`));
      if (g.parent) {
        await this.lp(`lp group ${name} parent set ${g.parent}`, lpSaid(`${name} had their existing parent groups cleared, and now only inherits ${g.parent} `));
      }
      for (const node of g.nodes) {
        await this.lp(`lp group ${name} permission set ${node} true`, lpSaid(`Set ${node} to true for ${name} `));
      }
    }
  }

  /** Puts a player in one group (or `default`, none of the three), replacing any other. */
  async put(player, group) {
    await this.lp(`lp user ${player} parent set ${group}`,
      lpSaid(`${player} had their existing parent groups cleared, and now only inherits ${group} `), `${player}'s group`);
  }

  /** Whether LuckPerms says an online player holds a node now (its "Result:" line). */
  async holds(player, node) {
    const line = await this.lp(`lp user ${player} permission check ${node}`, /\[LP\]\s+Result: (true|false|undefined)/, `a check of ${node} for ${player}`);
    return /Result: true/.test(line);
  }
}

module.exports = { Groups, GROUPS, BASELINE, nodesOf, USE, BUILD, ADMIN };
