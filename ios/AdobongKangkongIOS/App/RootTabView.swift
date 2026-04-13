import SwiftUI

struct RootTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                DashboardView()
            }
            .tabItem {
                Label("Dashboard", systemImage: "chart.bar.fill")
            }

            NavigationStack {
                FoodsListView()
            }
            .tabItem {
                Label("Foods", systemImage: "leaf.fill")
            }

            NavigationStack {
                RecipesView()
            }
            .tabItem {
                Label("Recipes", systemImage: "book.closed.fill")
            }

            NavigationStack {
                PlannerView()
            }
            .tabItem {
                Label("Planner", systemImage: "calendar")
            }

            NavigationStack {
                DayLogView()
            }
            .tabItem {
                Label("Log", systemImage: "list.bullet.rectangle")
            }

            NavigationStack {
                ShoppingView()
            }
            .tabItem {
                Label("Shopping", systemImage: "cart.fill")
            }

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Label("Settings", systemImage: "gearshape.fill")
            }
        }
    }
}
