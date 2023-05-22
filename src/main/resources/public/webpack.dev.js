const webpack = require("webpack");
const { merge } = require("webpack-merge");
const common = require("./webpack.common.js");

module.exports = merge(common.config, {
    mode: "development",
    devtool: "source-map",
    plugins: [
        new webpack.DefinePlugin({
            // this was a test: Didn't work. I tried it in the index.html and it was not translated.
            // Ended up accomplishing this using my 'cachebuster' option on HtmlWebpackPlugin instead.
            // WARNING: The 'stringify' here looks redundant but it's actually requird here by DefinePlugin
            BUILDTIME: JSON.stringify(common.formatDate(new Date())),
            PROFILE: JSON.stringify("dev")
        })
    ]
});
