const webpack = require('webpack');
const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyPlugin = require("copy-webpack-plugin");

//
// Save output JS in public/generated-js/index.js
//
module.exports = {
  target: 'es5',
  entry: {
    index: path.join(__dirname, '../src/index.ts')
  },
  output: {
    path: path.join(__dirname, '../../public/generated-js'),
    filename: '[name].js',
    chunkFormat: false
  },
  module: {
    rules: [
      {
        test: /\.(ts|js)x?$/,
        exclude: /node_modules/,
        loader: 'ts-loader',
      },
      {
        test: /\.css$/,
        use: [
          MiniCssExtractPlugin.loader,
          'css-loader',
        ],
      }
    ]
  },
  resolve: {
    extensions: ['.ts', '.tsx', '.js', '.css'],
    fallback: {
      "crypto": false,
      "fs": false,
      "stream": false,
    }
  },
  plugins: [
    new CopyPlugin({
      patterns: [
        // This one is needed because the maintainer did not fix the exports issue
        // https://github.com/brianvoe/slim-select/issues/269
        { from: "../node_modules/slim-select/dist/slimselect.min.css" },
        // The xlsx dependency is huge, we only serve it on the pages that use it
        { from: "../node_modules/xlsx/dist/xlsx.full.min.js" },
      ],
    }),
    new MiniCssExtractPlugin(),
  ]
};
