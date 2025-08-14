#!/bin/bash
echo "Cleaning build artifacts..."

rm -rf build/classes/*
rm -rf build/test-classes/*
rm -rf build/reports/*
rm -rf logs/*.log
rm -rf benchmarks/results/*

echo "Clean completed!"