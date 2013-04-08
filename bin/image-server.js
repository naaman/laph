var server = require('webserver').create();

service = server.listen(8080, function (request, response) {
    response.statusCode = 200;

    var req = JSON.parse(request.post);
    var page = require('webpage').create();

    page.viewportSize = { width: 600, height: 200 };
    page.open(req.input, function(status) {
//        console.log(status);
        response.write(page.renderBase64("PNG"));
        response.close();
    });
});
