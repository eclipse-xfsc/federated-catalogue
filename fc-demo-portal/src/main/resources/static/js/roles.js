$(document).ready(function() {

  $.ajax({
    type: 'GET',
    url: 'roles',
    dataType: 'json',
    success: function(response) {
      var data = response.map(function(name) { return { name: name }; });
      $('#rolesTable').DataTable({
        data: data,
        layout: {
          topStart: 'search',
          topEnd: 'pageLength',
          bottomStart: 'info',
          bottomEnd: 'paging'
        },
        initComplete: addSearchIcon,
        columns: [{ data: 'name', render: $.fn.dataTable.render.text() }]
      });
    },
    error: function(xhr) {
      var msg = 'You do not have permission to view roles.';
      var cls = 'alert-warning';
      if (xhr.status !== 403) {
        msg = (xhr.responseJSON && xhr.responseJSON.message) || 'Failed to load roles.';
        cls = 'alert-danger';
      }
      $('<div>', { class: 'alert ' + cls + ' mt-2' }).text(msg).appendTo($('#error-area').empty());
      $('#rolesTable').hide();
    }
  });

});

function addSearchIcon() {
  var $filter = $(this.api().table().container()).find('.dataTables_filter label');
  var $input = $filter.find('input').detach();
  $filter.empty().append(
    $('<div class="input-group input-group-sm">').append(
      $('<span class="input-group-text"><i class="bi bi-search"></i></span>'),
      $input
    )
  );
}
