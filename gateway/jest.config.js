'use strict';

/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'node',
  rootDir: '.',
  roots: ['<rootDir>/__tests__', '<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.js'],
  testTimeout: 15000,
  // Suppress console.warn output in tests to keep output clean
  // (tests that expect warnings mock them explicitly)
};
