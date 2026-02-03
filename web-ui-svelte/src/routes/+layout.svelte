<script>
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';

	import { Navbar, NavBrand, NavLi, NavUl, NavHamburger } from "flowbite-svelte";
  import { Footer, FooterLinkGroup, FooterLink, ImagePlaceholder, TextPlaceholder, Skeleton, FooterCopyright } from "flowbite-svelte";

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<Navbar>
  <NavBrand href="/">
    <!-- <img src="/images/flowbite-svelte-icon-logo.svg" class="me-3 h-6 sm:h-9" alt="Flowbite Logo" /> -->
    <span class="self-center text-xl font-semibold whitespace-nowrap dark:text-white">Data application</span>
  </NavBrand>
  <NavHamburger />
  <NavUl>
    <NavLi href="/">Home</NavLi>
    <NavLi href="/live">Live</NavLi>
  </NavUl>
</Navbar>

{@render children()}

<Footer class="absolute start-0 bottom-0 z-20 w-full border-t border-gray-200 bg-white p-4 shadow-sm md:flex md:items-center md:justify-between md:p-6 dark:border-gray-600 dark:bg-gray-800">
  <FooterCopyright href="/" by="Kinnovatio" year={2026} />
  <FooterLinkGroup class="mt-3 flex flex-wrap items-center text-sm text-gray-500 sm:mt-0 dark:text-gray-400">
    <FooterLink href="/">About</FooterLink>
    <FooterLink href="/">Privacy Policy</FooterLink>
    <FooterLink href="/">Licensing</FooterLink>
    <FooterLink href="/">Contact</FooterLink>
  </FooterLinkGroup>
</Footer>