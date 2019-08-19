if (!!window.EventSource) {
    var stringSource = new EventSource(jsRoutes.controllers.JavaEventSourceController.streamClock().url);
    stringSource.addEventListener('message', function(e) {
        $('#clock').html(e.data.replace(/(\d)/g, '<span>$1</span>'))
    });
}
