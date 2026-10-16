import assert from 'node:assert/strict';
import { test } from 'node:test';
import { getLoopEdgeIds } from './workflowLoop.ts';

const edge = (source, target, id = `${source}-${target}`) => ({ id, source, target });
const loopIds = (edges) => [...getLoopEdgeIds(edges)].sort();

test('ordinary branches and merging paths are not loops', () => {
  assert.deepEqual(loopIds([
    edge('input', 'condition'), edge('condition', 'a'), edge('condition', 'b'),
    edge('a', 'output'), edge('b', 'output'),
  ]), []);
});

test('identifies the complete loop, excluding entry and exit', () => {
  const edges = [
    edge('input', 'a'), edge('a', 'b'), edge('b', 'condition'),
    edge('condition', 'a'), edge('condition', 'output'),
  ];
  assert.deepEqual(loopIds(edges), ['a-b', 'b-condition', 'condition-a']);
  assert.deepEqual(loopIds([...edges].reverse()), loopIds(edges));
  assert.deepEqual(loopIds(edges.filter(({ id }) => id !== 'condition-a')), []);
});

test('supports self loops, disconnected loops and multiple branches to one target', () => {
  assert.deepEqual(loopIds([
    edge('a', 'a'), edge('b', 'c'), edge('c', 'b'),
    edge('condition', 'd', 'branch-1'), edge('condition', 'd', 'default'), edge('d', 'condition'),
  ]), ['a-a', 'b-c', 'branch-1', 'c-b', 'd-condition', 'default']);
  assert.deepEqual(loopIds([]), []);
});
