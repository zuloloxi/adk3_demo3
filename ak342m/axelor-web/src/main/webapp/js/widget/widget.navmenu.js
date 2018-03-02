/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
(function() {

var module = angular.module('axelor.ui');

NavMenuCtrl.$inject = ['$scope', '$element', 'MenuService', 'NavService'];
function NavMenuCtrl($scope, $element, MenuService, NavService) {

	$scope.menus = []; 	// the first four visible menus
	$scope.more = [];	// rest of the menus

	var hasSideBar = __appSettings['view.menubar.location'] !== 'top';

	MenuService.all().then(function(response) {
		var res = response.data,
			data = res.data;

		var items = {};
		var all = [];

		_.each(data, function(item) {
			items[item.name] = item;
			if (item.children === undefined) {
				item.children = [];
			}

		});

		_.each(data, function(item) {

			if (hasSideBar && !item.parent && !item.top) {
				return;
			}

			if (item.parent == null) {
				return all.push(item);
			}
			var parent = items[item.parent];
			if (parent) {
				parent.children.push(item);
			}
		});

		$scope.menus = all;
		$scope.more = all;

		$scope.extra = {
			title: 'More',
			children: $scope.more
		};
	});

	this.isSubMenu = function(item) {
		return item && item.children && item.children.length > 0;
	};

	this.onItemClick = function(item) {
		if (this.isSubMenu(item) || item.isFolder) {
			return;
		}
		NavService.openTabByName(item.action);
	};

	$scope.hasImage = function (menu) {
		return menu.icon && menu.icon.indexOf('fa-') !== 0;
	};

	$scope.hasIcon = function (menu) {
		return menu.icon && menu.icon.indexOf('fa-') === 0;
	};

	$scope.hasText = function (menu) {
		return !menu.icon || menu.icon.indexOf('empty') === -1;
	}
}

module.directive('navMenuBar', function() {

	return {

		replace: true,

		controller: NavMenuCtrl,

		scope: true,

		link: function(scope, element, attrs, ctrl) {

			var elemTop,
				elemSub,
				elemMore;

			var siblingsWidth = 0;
			var adjusting = false;
			
			element.hide();
			
			function adjust() {
				
				if (adjusting) {
					return;
				}
				
				adjusting = true;

				var count = 0;
				var parentWidth = element.parent().width() - 32;

				elemMore.hide();
				elemTop.hide();
				elemSub.hide();
				
				while (count < elemTop.size()) {
					var elem = $(elemTop[count]).show();
					var width = siblingsWidth + element.width();
					if (width > parentWidth) {
						elem.hide();
						
						// show more...
						elemMore.show();
						width = siblingsWidth + element.width();
						if (width > parentWidth) {
							count--;
							$(elemTop[count]).hide();
						}
						
						break;
					}
					count++;
				}
				
				if (count === elemTop.size()) {
					elemMore.hide();
				}
				while(count < elemTop.size()) {
					$(elemSub[count++]).show();
				}
				
				adjusting = false;
			}
			
			function setup() {
				
				element.find('.dropdown-toggle').dropdown();
				element.find('.dropdown.nav-menu').hover(function() {
					$(this).addClass('open');
				}, function() {
					$(this).removeClass('open');
				});
				element.siblings().each(function () {
					siblingsWidth += $(this).width();
				});

				elemTop = element.find('.nav-menu.dropdown:not(.nav-menu-more)');
				elemMore = element.find('.nav-menu.dropdown.nav-menu-more');
				elemSub = elemMore.find('.dropdown-menu:first > .dropdown-submenu');

				element.show();
				adjust();
				
				$(window).on("resize.menubar", adjust);
			}

			element.on('$destroy', function () {
				if (element) {
					$(window).off("resize.menubar");
					element = null;
				}
			});
			
			var unwatch = scope.$watch('menus', function(menus, old) {
				if (!menus || menus.length == 0  || menus === old) {
					return;
				}
				unwatch();
				setTimeout(setup, 100);
			});
		},

		template:
			"<ul class='nav nav-menu-bar'>" +
				"<li class='nav-menu dropdown' ng-class='{empty: !hasText(menu)}' ng-repeat='menu in menus'>" +
					"<a href='' class='dropdown-toggle' data-toggle='dropdown'>" +
						"<img ng-if='hasImage(menu)' ng-src='{{menu.icon}}'> " +
						"<i ng-if='hasIcon(menu)' class='fa {{menu.icon}}'></i> " +
						"<span ng-if='hasText(menu)' ng-bind='menu.title'></span> " +
						"<b class='caret'></b>" +
					"</a>" +
					"<ul nav-menu='menu'></ul>" +
				"</li>" +
				"<li class='nav-menu nav-menu-more dropdown' style='display: none;'>" +
					"<a href='' class='dropdown-toggle' data-toggle='dropdown'>" +
						"<span x-translate>More</span>" +
						"<b class='caret'></b>" +
					"</a>" +
					"<ul nav-menu='extra'></ul>" +
				"</li>" +
			"</ul>"
	};
});

module.directive('navMenu', function() {

	return {
		replace: true,
		require: '^navMenuBar',
		scope: {
			menu: '=navMenu'
		},
		link: function(scope, element, attrs, ctrl) {

		},
		template:
			"<ul class='dropdown-menu'>" +
				"<li ng-repeat='item in menu.children' nav-menu-item='item'>" +
			"</ul>"
	};
});

module.directive('navMenuItem', ['$compile', function($compile) {

	return {
		replace: true,
		require: '^navMenuBar',
		scope: {
			item: '=navMenuItem'
		},
		link: function(scope, element, attrs, ctrl) {

			var item = scope.item;

			scope.isSubMenu = ctrl.isSubMenu(item);
			scope.isActionMenu = item.action != null;

			scope.onClick = function(e, item) {
				ctrl.onItemClick(item);
				element.parents('.dropdown').dropdown('toggle');
			};

			scope.cssClass = function() {
				if (ctrl.isSubMenu(item)) return 'dropdown-submenu';
				if (item.action) return 'action-menu';
			};

			if (ctrl.isSubMenu(item)) {
				$compile('<ul nav-menu="item"></ul>')(scope, function(cloned, scope) {
					element.append(cloned);
				});
			}
		},
		template:
			"<li ng-class='cssClass()'>" +
				"<a href='' ng-click='onClick($event, item)'>{{item.title}}</a>" +
			"</li>"
	};
}]);

}).call(this);
