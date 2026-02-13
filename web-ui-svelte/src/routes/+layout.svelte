<script>
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';

  import { page } from "$app/state";
	import { Navbar, NavBrand, NavLi, NavUl, NavHamburger } from "flowbite-svelte";
  import { Footer, FooterLinkGroup, FooterLink, FooterCopyright } from "flowbite-svelte";

  let activeUrl = $derived(page.url.pathname);

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<Navbar class="border-b border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-900">
  <NavBrand href="/">
    <!-- <img src="/images/flowbite-svelte-icon-logo.svg" class="me-3 h-6 sm:h-9" alt="Flowbite Logo" /> -->
    <span class="self-center text-xl font-semibold whitespace-nowrap dark:text-white">Formula 1</span>
  </NavBrand>
  <NavHamburger />
  <NavUl {activeUrl}>
    <NavLi href="/">Home</NavLi>
    <NavLi href="/live">Live</NavLi>
  </NavUl>
</Navbar>
<div class="flex mx-auto px-4 py-0 border-b border-gray-200 bg-gray-100 dark:border-gray-700 dark:bg-gray-900">
    <h3>Next Race</h3>
</div>

{@render children()}

<Footer class="mt-3 flex flex-wrap items-center gap-y-4 border-t border-gray-200 text-sm text-gray-500 sm:mt-0 dark:border-gray-700 dark:text-gray-400">
  <FooterCopyright href="/" by="Kinnovatio" year={2026} />
  <FooterLinkGroup class="mt-3 flex flex-wrap items-center text-sm text-gray-500 sm:mt-0 dark:text-gray-400">
    <FooterLink href="/">About</FooterLink>
    <FooterLink href="/">Privacy Policy</FooterLink>
    <FooterLink href="/">Licensing</FooterLink>
    <FooterLink href="/">Contact</FooterLink>
  </FooterLinkGroup>
  <p class="text-xs text-gray-500 dark:text-gray-400">
    This project is unofficial and are not associated in any way with the Formula 1 companies. 
    F1, FORMULA ONE, FORMULA 1, FIA FORMULA ONE WORLD CHAMPIONSHIP, 
    GRAND PRIX and related marks are trade marks of Formula One Licensing B.V.</p>
</Footer>